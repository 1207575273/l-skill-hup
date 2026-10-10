package com.landray.skillhub.interfaces.rest.skill;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.install.SkillDownloadApplicationService;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.interfaces.rest.support.ClientIpResolver;

/**
 * {@code GET /skills/{id}/versions/{version}/download}:下载制品 zip(业务建模文档 5.12)。
 *
 * <p><b>单独成一个 Controller,不加进已有的 {@code SkillController}</b>——那个类与 {@code SkillApplicationService}
 * 归另一条并行任务线(上传管线)维护,这里改用自己的 {@link SkillDownloadApplicationService},避免同一个文件被
 * 两条任务线同时改动而冲突。
 *
 * <p>匿名可用(业务建模文档 5.16)。字节拷贝这段是纯 I/O 管道,业务判断(要不要计数、算不算追加下载)全部委托给
 * 应用服务,Controller 只做参数收集与流的搬运(backend/CLAUDE.md 第一节"薄控制器")。
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillDownloadController {

    private static final Logger log = LoggerFactory.getLogger(SkillDownloadController.class);

    private static final String CONTENT_TYPE_ZIP = "application/zip";
    private static final String CONTENT_DISPOSITION_TEMPLATE = "attachment; filename=\"%s-%s.zip\"";

    private final SkillDownloadApplicationService skillDownloadApplicationService;

    public SkillDownloadController(SkillDownloadApplicationService skillDownloadApplicationService) {
        this.skillDownloadApplicationService = skillDownloadApplicationService;
    }

    @GetMapping("/{id}/versions/{version}/download")
    public void download(
            @PathVariable long id, @PathVariable String version, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        SkillArtifact artifact = skillDownloadApplicationService.resolveArtifact(id, version);

        response.setContentType(CONTENT_TYPE_ZIP);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                CONTENT_DISPOSITION_TEMPLATE.formatted(artifact.skillName(), artifact.version()));
        // 长度取自 artifact 自己,不再回存储问一次。SkillArtifact 是 join skill + skill_version 的窄投影,
        // size_bytes 就在里面(上传管线落库时算的,与 sha256 出自同一份字节),上一行 resolveArtifact 刚查出来。
        //
        // 换成对象存储后这个区别是实打实的:问一次存储就是一次 HeadObject 跨境往返(实测到香港 RTT 350ms),
        // 而下载本身只需要一次 GetObject。本地盘时代这一次调用几乎免费,所以当初那么写并不错;
        // 存储换了,代价变了,写法跟着变。
        response.setContentLengthLong(artifact.sizeBytes());

        boolean transferCompleted = streamArtifact(artifact, response);
        if (!transferCompleted) {
            return;
        }

        if (shouldCount(request)) {
            skillDownloadApplicationService.recordDownload(
                    id, artifact.skillVersionId(), ClientIpResolver.resolve(request));
        }
    }

    /**
     * @return {@code true} 表示字节已完整写出,调用方可以放心触发计数;{@code false} 表示传输中途被打断
     *     (客户端关闭连接是正常的网络行为,不重试、不当成错误上抛——响应此时往往已提交,再抛异常也无法改变
     *     响应状态,徒增一条无意义的 500 日志)。
     */
    private boolean streamArtifact(SkillArtifact artifact, HttpServletResponse response) throws IOException {
        try (InputStream in = skillDownloadApplicationService.openArtifact(artifact.artifactKey())) {
            in.transferTo(response.getOutputStream());
            return true;
        } catch (IOException e) {
            log.debug(
                    "制品下载传输中断,跳过计数, skillId={}, version={}, reason={}",
                    artifact.skillId(),
                    artifact.version(),
                    e.getMessage());
            return false;
        }
    }

    /**
     * 业务建模文档 5.12 的两条约束:{@code HEAD} 请求不计(Spring MVC 对 {@code @GetMapping} 默认兼容处理
     * {@code HEAD},方法体会照常执行,必须显式排除);带 {@code Range} 头的请求不计(本实现不支持范围请求,
     * 但仍需遵守"不计"这条规则,不能因为没实现 206 就假装它是一次普通的完整下载)。
     */
    private boolean shouldCount(HttpServletRequest request) {
        boolean isHeadRequest = HttpMethod.HEAD.name().equalsIgnoreCase(request.getMethod());
        boolean isRangeRequest = request.getHeader(HttpHeaders.RANGE) != null;
        return !isHeadRequest && !isRangeRequest;
    }
}
