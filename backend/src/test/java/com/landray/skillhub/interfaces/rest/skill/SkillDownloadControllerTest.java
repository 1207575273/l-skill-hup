package com.landray.skillhub.interfaces.rest.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.landray.skillhub.application.install.SkillDownloadApplicationService;
import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.model.SkillStatus;

/**
 * 直接实例化 Controller,不起 {@code @WebMvcTest}——本类要精确控制请求方法(HEAD)与 {@code Range} 头这两个
 * 真实 Servlet 容器语义细节(业务建模文档 5.12:HEAD 与 Range 请求不计入下载计数),用
 * {@code MockHttpServletRequest}/{@code MockHttpServletResponse} 比依赖 MockMvc 对 HEAD 的容器级分发行为
 * 更直接、更不依赖测试基础设施的隐式假设。
 */
@ExtendWith(MockitoExtension.class)
class SkillDownloadControllerTest {

    @Mock
    private SkillDownloadApplicationService skillDownloadApplicationService;

    private SkillDownloadController controller;

    @BeforeEach
    void setUp() {
        controller = new SkillDownloadController(skillDownloadApplicationService);
    }

    private SkillArtifact artifact() {
        return new SkillArtifact(
                10L, 1L, "code-review", "1.0.0", "artifacts/1/1.0.0.zip", "0".repeat(64), 3L, SkillStatus.PUBLISHED,
                Certification.NONE, null);
    }

    private static final class ThrowingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("broken pipe");
        }
    }

    @Test
    @DisplayName("正常 GET 请求:写出完整字节、设置正确响应头、触发下载计数")
    void should_streamBytesSetHeadersAndRecordDownload_when_normalGetRequest() throws IOException {
        when(skillDownloadApplicationService.resolveArtifact(1L, "1.0.0")).thenReturn(artifact());
        when(skillDownloadApplicationService.openArtifact("artifacts/1/1.0.0.zip"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills/1/versions/1.0.0/download");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.download(1L, "1.0.0", request, response);

        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(response.getContentType()).isEqualTo("application/zip");
        // Content-Length 来自 SkillArtifact.sizeBytes(夹具里是 3),不是回存储问出来的 ——
        // 这条断言连同上面"没有 sizeOf stub"一起,挡住有人把它改回去问一次存储:
        // 对象存储下那是一次白给的跨境往返。
        assertThat(response.getContentLength()).isEqualTo(3);
        assertThat(response.getHeader("Content-Disposition")).contains("code-review-1.0.0.zip");
        verify(skillDownloadApplicationService).recordDownload(1L, 10L, "127.0.0.1");
    }

    @Test
    @DisplayName("HEAD 请求不计入下载计数,即便响应体逻辑照常执行")
    void should_notRecordDownload_when_headRequest() throws IOException {
        when(skillDownloadApplicationService.resolveArtifact(1L, "1.0.0")).thenReturn(artifact());
        when(skillDownloadApplicationService.openArtifact(any()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        MockHttpServletRequest request =
                new MockHttpServletRequest("HEAD", "/api/v1/skills/1/versions/1.0.0/download");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.download(1L, "1.0.0", request, response);

        verify(skillDownloadApplicationService, never()).recordDownload(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("带 Range 头的请求不计入下载计数")
    void should_notRecordDownload_when_rangeHeaderPresent() throws IOException {
        when(skillDownloadApplicationService.resolveArtifact(1L, "1.0.0")).thenReturn(artifact());
        when(skillDownloadApplicationService.openArtifact(any()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/skills/1/versions/1.0.0/download");
        request.addHeader("Range", "bytes=0-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.download(1L, "1.0.0", request, response);

        verify(skillDownloadApplicationService, never()).recordDownload(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("传输中途中断(客户端断开连接):不抛异常、不计入下载计数")
    void should_notThrowAndNotRecordDownload_when_transferInterrupted() throws IOException {
        when(skillDownloadApplicationService.resolveArtifact(1L, "1.0.0")).thenReturn(artifact());
        when(skillDownloadApplicationService.openArtifact(any())).thenReturn(new ThrowingInputStream());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills/1/versions/1.0.0/download");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.download(1L, "1.0.0", request, response);

        verify(skillDownloadApplicationService, never()).recordDownload(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("X-Forwarded-For 存在时用它的第一段作为计数用的 clientIp")
    void should_useForwardedForHeader_when_present() throws IOException {
        when(skillDownloadApplicationService.resolveArtifact(1L, "1.0.0")).thenReturn(artifact());
        when(skillDownloadApplicationService.openArtifact(any()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/skills/1/versions/1.0.0/download");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.download(1L, "1.0.0", request, response);

        verify(skillDownloadApplicationService).recordDownload(eq(1L), eq(10L), eq("203.0.113.9"));
    }
}
