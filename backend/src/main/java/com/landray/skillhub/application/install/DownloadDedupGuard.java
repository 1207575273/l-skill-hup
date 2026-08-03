package com.landray.skillhub.application.install;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 下载计数的时间窗去重:同一 {@code clientIp + versionId} 在 10 分钟内只计一次(业务建模文档 5.12)。
 *
 * <p><b>内存实现,不建表</b>——文档明写"不建表,明细表是 P3"。下载是这个平台唯一一个匿名可触发的写操作,浏览器预取、
 * IM 链接预览抓取、企业 EDR 的 URL 扫描都会静默地把计数刷高,而 {@code download_count} 直接决定热门榜排序,
 * 这个时间窗就是唯一的防线。
 *
 * <p>用 {@link ConcurrentHashMap#compute} 而不是"先 get 判断再 put"——后者在并发下会有两个线程都读到"未在窗口内"
 * 从而都判定"应计数"的竞态,{@code compute} 对同一个 key 是原子的,消除了这个窗口。
 *
 * <p><b>不做容量上限或过期清理</b>(YAGNI):key 是 {@code clientIp:versionId} 的组合,在几十人团队 + 几百个 skill
 * 版本的量级下,稳态下的不同 key 数量是有界的小值(新 key 只在"新的 IP 第一次下载某个新版本"时出现,而 IP 与
 * skill 版本两个维度本身都不会无限增长);老 key 会随着时间推移被同一对 clientIp+versionId 的后续请求覆盖更新,
 * 不是无限堆积。量级真的失控时(P3 有了安装明细表)再回头评估要不要换成带过期策略的缓存。
 */
@Component
public class DownloadDedupGuard {

    static final Duration WINDOW = Duration.ofMinutes(10);

    private static final String UNKNOWN_CLIENT_IP = "unknown";

    private final ConcurrentHashMap<String, Instant> lastCountedAt = new ConcurrentHashMap<>();

    /** @return {@code true} 表示这次下载应当计入 {@code skill.download_count},调用方随即触发一次原子自增。 */
    public boolean shouldCount(String clientIp, long skillVersionId, Instant now) {
        String key = (clientIp == null || clientIp.isBlank() ? UNKNOWN_CLIENT_IP : clientIp) + ":" + skillVersionId;
        boolean[] shouldCount = new boolean[1];
        lastCountedAt.compute(key, (ignoredKey, previous) -> {
            if (previous != null && previous.plus(WINDOW).isAfter(now)) {
                shouldCount[0] = false;
                return previous;
            }
            shouldCount[0] = true;
            return now;
        });
        return shouldCount[0];
    }
}
