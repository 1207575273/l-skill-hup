package com.landray.skillhub.application;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.landray.skillhub.application.auth.AuthenticatedUser;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSortOption;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.domain.model.SkillVersionFile;
import com.landray.skillhub.domain.model.SkillVersionFileContent;
import com.landray.skillhub.domain.model.SkillVersionFilePreviewer;
import com.landray.skillhub.domain.model.UserRole;
import com.landray.skillhub.domain.repository.SkillRepository;
import com.landray.skillhub.domain.service.SkillArtifactManifestReader;
import com.landray.skillhub.domain.storage.ArtifactStorage;
import com.landray.skillhub.shared.exception.AccessDeniedException;
import com.landray.skillhub.shared.exception.ResourceNotFoundException;
import com.landray.skillhub.shared.logging.AuditLog;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 用例编排:读侧(列表、详情、版本列表)+ 写侧(改元数据、下架/恢复)。事务边界只在这一层
 * (backend/CLAUDE.md 第五节)。
 *
 * <p>page / size 的默认值与夹紧(size 上限 100)在这里做,不在 {@link SkillSearchCriteria} 的紧凑构造器里——
 * "把不合法的外部输入变成合法值"是解释请求的职责,{@code SkillSearchCriteria} 只负责"值一旦构造出来就合法"。
 *
 * <p>写侧三个方法(改元数据、下架、恢复)共用同一条权限规则——owner 或 ADMIN(业务建模文档 5.16),这是一条
 * <b>资源级</b>权限判断(取决于"这是谁的 skill"),{@code @RequireLogin} 只能确认"登录了没有",判断不了
 * "是不是这个 skill 的 owner",所以校验必须落在这一层而不是拦截器。
 */
@Service
public class SkillApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillApplicationService.class);

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int MIN_PAGE = 1;
    private static final int MIN_SIZE = 1;

    private static final String SKILL_NOT_FOUND_CODE = "SKILL_NOT_FOUND";
    private static final String SKILL_VERSION_NOT_FOUND_CODE = "SKILL_VERSION_NOT_FOUND";
    private static final String SKILL_VERSION_FILE_NOT_FOUND_CODE = "SKILL_VERSION_FILE_NOT_FOUND";

    private final SkillRepository skillRepository;
    private final SkillTagAssignmentService tagAssignmentService;
    private final AuditLog auditLog;
    private final ArtifactStorage artifactStorage;

    /** 无状态的纯 Java 解析器,domain 服务不是 Spring bean,直接 new(与 SkillUploadApplicationService 同一模式)。 */
    private final SkillArtifactManifestReader manifestReader = new SkillArtifactManifestReader();

    public SkillApplicationService(
            SkillRepository skillRepository,
            SkillTagAssignmentService tagAssignmentService,
            AuditLog auditLog,
            ArtifactStorage artifactStorage) {
        this.skillRepository = skillRepository;
        this.tagAssignmentService = tagAssignmentService;
        this.auditLog = auditLog;
        this.artifactStorage = artifactStorage;
    }

    @Transactional(readOnly = true)
    public PageResult<Skill> listSkills(
            String sourceParam,
            List<Long> tagIds,
            boolean certifiedOnly,
            String keyword,
            Long ownerUserId,
            String sortParam,
            Integer pageParam,
            Integer sizeParam) {
        SkillSource source = sourceParam == null || sourceParam.isBlank() ? null : SkillSource.fromParam(sourceParam);
        SkillSortOption sort = SkillSortOption.fromParam(sortParam);
        int page = normalizePage(pageParam);
        int size = normalizeSize(sizeParam);

        SkillSearchCriteria criteria =
                new SkillSearchCriteria(source, tagIds, certifiedOnly, keyword, ownerUserId, sort, page, size);
        log.debug(
                "查询 skill 列表, source={}, tagCount={}, certifiedOnly={}, sort={}, page={}, size={}",
                source,
                tagIds == null ? 0 : tagIds.size(),
                certifiedOnly,
                sort,
                page,
                size);
        return skillRepository.search(criteria);
    }

    /**
     * 管理视图的 skill 列表:<b>含已下架</b>,给管理页用。
     *
     * <p><b>为什么不是给 {@code listSkills} 加一个 {@code status} 参数,而是单开一个方法。</b>公开列表是匿名可用的,
     * 一旦它能接收 status,今后每次改那条链路都得重新论证一遍"这个调用方能不能看到下架内容",而这种论证迟早会
     * 有人漏掉 —— 漏掉的后果是把下架内容泄漏给所有人。单开一个方法则只有一条无条件规则:必须登录,且作用域被
     * 强制收窄,没有任何分支可走错。
     *
     * <p><b>ownerUserId 由服务端按当前身份强制覆盖,不接受调用方传入。</b>非 ADMIN 一律只能看自己的;ADMIN 才
     * 看得到全部。让前端传 ownerUserId 等于把"我是谁"交给客户端声明,那是最典型的越权入口。
     */
    @Transactional(readOnly = true)
    public PageResult<Skill> listManagedSkills(
            AuthenticatedUser actingUser,
            String statusParam,
            String keyword,
            String sortParam,
            Integer pageParam,
            Integer sizeParam) {
        Long scopedOwnerUserId = actingUser.role() == UserRole.ADMIN ? null : actingUser.userId();
        SkillSearchCriteria criteria = new SkillSearchCriteria(
                null,
                List.of(),
                false,
                keyword,
                scopedOwnerUserId,
                resolveManagedStatuses(statusParam),
                SkillSortOption.fromParam(sortParam),
                normalizePage(pageParam),
                normalizeSize(sizeParam));

        log.debug(
                "查询管理视图 skill 列表, operator={}, scopedOwnerUserId={}, statuses={}, page={}",
                actingUser.userId(),
                scopedOwnerUserId,
                criteria.statuses(),
                criteria.page());
        return skillRepository.search(criteria);
    }

    /**
     * 管理视图的状态筛选:不传 = 两种都要。
     *
     * <p>默认给全集而不是默认 {@code PUBLISHED},因为这个页面的存在理由就是"下架之后还能找到它"——
     * 默认只显示已发布,等于把这个页面唯一的独占能力藏在一次筛选点击之后。
     */
    private List<SkillStatus> resolveManagedStatuses(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return List.of(SkillStatus.PUBLISHED, SkillStatus.UNLISTED);
        }
        return List.of(SkillStatus.fromParam(statusParam));
    }

    @Transactional(readOnly = true)
    public Skill getSkillDetail(long skillId) {
        return skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
    }

    /**
     * 详情页"文件树":现读已发布制品 zip 的中央目录,不落库(方案取舍见 {@link SkillArtifactManifestReader}
     * 类注释)。<b>刻意不加 {@code @Transactional}</b>——这里不碰数据库,只碰 {@link ArtifactStorage}。
     *
     * <p>制品缺失(理论上不该发生,但不排除存储被误清空)或不是合法 zip(制品损坏)都<b>不应该让详情接口整体
     * 失败</b>——详情页的主体是元数据,文件清单只是附加信息,降级为空清单,WARN 留痕供巡检定位坏制品
     * (backend/CLAUDE.md 6.6)。这是本方法唯一的编排职责,解析本身交给 {@link SkillArtifactManifestReader}。
     */
    public List<SkillVersionFile> getLatestVersionFileManifest(Skill skill) {
        SkillVersion latestVersion = skill.latestVersion();
        if (latestVersion == null || latestVersion.artifactKey() == null) {
            return List.of();
        }
        // 流的关闭责任交给 SkillArtifactManifestReader.read(...)(它内部就是一个 try-with-resources),
        // 这里不重复用 try-with-resources 包一层——ResourceNotFoundException(制品不存在)与
        // UncheckedIOException(制品不是合法 zip)都是未受检异常,不需要额外声明/捕获 IOException。
        try {
            InputStream in = artifactStorage.openRead(latestVersion.artifactKey());
            return manifestReader.read(in);
        } catch (RuntimeException e) {
            log.warn(
                    "文件清单读取失败,详情页文件树将为空, skillId={}, artifactKey={}",
                    skill.id(),
                    latestVersion.artifactKey(),
                    e);
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<SkillVersion> listVersions(long skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw notFound(skillId);
        }
        return skillRepository.findVersionsBySkillId(skillId);
    }

    /**
     * 详情页"文件预览":版本内某一个具体文件的内容(点开文件树上的一个节点)。<b>刻意不加 {@code @Transactional}</b>
     * ——除了一次只读的 {@code findVersionArtifactKey} 查询外,主体工作是打开 {@link ArtifactStorage} 与解析 zip,
     * 不需要事务边界。
     *
     * <p>{@code path} 来自 HTTP 请求,是外部输入,必须当成攻击面(根 CLAUDE.md 第七节):即使只在 zip 条目名里做
     * 精确字符串匹配、不碰文件系统,仍先用 {@link SkillArtifactManifestReader#isSafeRelativePath} 快速拒绝
     * {@code ..}/绝对路径/控制字符,再进入清单匹配——两次校验的理由与边界见该方法的类注释。
     *
     * <p>{@code versionId} 必须真的属于这个 {@code skillId},由 {@link SkillRepository#findVersionArtifactKey}
     * 的 SQL 联合条件天然保证(需求第 5 条)。版本不存在、版本属于别的 skill、路径不安全、清单里没有这个文件、
     * 制品缺失或损坏——这五种情况统一表现为 404,不额外区分向调用方泄漏的信息,只在服务端日志留痕。
     *
     * <p>制品缺失或不是合法 zip:与 {@link #getLatestVersionFileManifest} 同样的读取路径,但降级口径不同——那边
     * 清单可以退化成空列表,这里用户明确点了一个文件,没有"退化"的余地,404 更诚实。WARN 留痕(backend/CLAUDE.md
     * 6.6:安全事件一律 WARN 起步),带 skillId / artifactKey,供巡检定位坏制品。
     */
    public SkillVersionFileContent getVersionFileContent(long skillId, long versionId, String path) {
        if (!SkillArtifactManifestReader.isSafeRelativePath(path)) {
            log.warn("拒绝不安全的版本文件预览路径, skillId={}, versionId={}", skillId, versionId);
            throw fileNotFound(skillId, versionId);
        }
        String artifactKey = skillRepository
                .findVersionArtifactKey(skillId, versionId)
                .orElseThrow(() -> versionNotFound(skillId, versionId));

        byte[] rawContent;
        try {
            InputStream in = artifactStorage.openRead(artifactKey);
            rawContent = manifestReader.readFileContent(in, path).orElse(null);
        } catch (RuntimeException e) {
            log.warn(
                    "版本文件预览读取失败,制品可能缺失或损坏, skillId={}, versionId={}, artifactKey={}",
                    skillId,
                    versionId,
                    artifactKey,
                    e);
            throw fileNotFound(skillId, versionId);
        }
        if (rawContent == null) {
            throw fileNotFound(skillId, versionId);
        }

        return SkillVersionFilePreviewer.preview(path, rawContent);
    }

    /**
     * 改标签 / 来源 / 展示名(owner / ADMIN),{@code name} 不在参数列表里——它不可改
     * (业务建模文档 5.16),从结构上就不给"漏传了校验"这种回归留空间。
     */
    @Transactional
    public Skill updateMetadata(
            long skillId,
            String displayName,
            String sourceParam,
            List<Long> curatedTagIds,
            List<String> freeTagNames,
            AuthenticatedUser actingUser) {
        Skill skill = skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
        requireOwnerOrAdmin(skill, actingUser);

        SkillSource source = SkillSource.fromParam(sourceParam);
        List<Long> tagIds = tagAssignmentService.resolveTagIds(curatedTagIds, freeTagNames, actingUser.userId());

        skillRepository.updateMetadata(skillId, displayName, source);
        tagAssignmentService.replaceSkillTags(skillId, tagIds);

        log.info(
                "skill 元数据已更新, skillId={}, displayName={}, source={}, operator={}",
                skillId,
                displayName,
                source,
                actingUser.userId());
        return requireSkill(skillId);
    }

    /** 下架(owner / ADMIN),只改 status,不动版本、评论、表态、收藏(业务建模文档 5.15)。 */
    @Transactional
    public Skill unlist(long skillId, AuthenticatedUser actingUser) {
        return changeStatus(skillId, SkillStatus.UNLISTED, actingUser);
    }

    /** 恢复(owner / ADMIN)。 */
    @Transactional
    public Skill relist(long skillId, AuthenticatedUser actingUser) {
        return changeStatus(skillId, SkillStatus.PUBLISHED, actingUser);
    }

    /**
     * 物理删除 skill(owner / ADMIN)。<b>不可逆,没有回收站。</b>
     *
     * <p>业务建模文档 4.8 原本写着"不提供物理删除",理由是 skill 一旦被人装过就有了外部引用。这条已按需求推翻,
     * 但那个理由依然成立,所以代价必须被正面处理:已流传的安装单在渲染时会明确打印"该 skill 已被删除,跳过",
     * 而不是静默少装几个(见 {@code InstallApplicationService})。
     *
     * <p><b>三段顺序都是承重的,不能调换:</b>
     *
     * <ol>
     *   <li><b>先取 artifactKey。</b>删完库行就再也不知道该删哪些文件,制品会永久残留在磁盘上,而且没有任何报错。
     *   <li><b>再删库行(同一事务)。</b>标签走 {@code replaceSkillTags(skillId, [])},复用孤儿 FREE 标签的清理
     *       规则(5.7),不在这里重写一遍。
     *   <li><b>最后删制品文件,而且只做尽力而为。</b>反过来先删文件的话,库操作一旦失败就留下一个查得到、装不了的
     *       skill —— 那比留几个孤儿文件糟得多。文件删不掉只记 WARN 不抛异常:此刻库行已经删了,再把异常抛给
     *       调用方,前端会显示"删除失败"而实际上已经删掉了,那是最容易让人做出错误后续动作的一种反馈。
     * </ol>
     */
    @Transactional
    public void delete(long skillId, AuthenticatedUser actingUser) {
        Skill skill = skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
        requireOwnerOrAdmin(skill, actingUser);

        List<String> artifactKeys = skillRepository.findVersionArtifactKeys(skillId);
        tagAssignmentService.replaceSkillTags(skillId, List.of());
        skillRepository.deleteSkillCascade(skillId);

        // 物理删除比下架烈度高一档:下架可恢复,这个不可以。审计日志按 WARN 记,并且带上 name 与版本数——
        // 事后追查时 skillId 已经查不到任何东西了,只有当时记下来的名字还能对上人。
        log.warn(
                "skill 已被物理删除, skillId={}, name={}, versionCount={}, operator={}",
                skillId,
                skill.name().value(),
                artifactKeys.size(),
                actingUser.userId());
        auditLog.info(
                "skill 物理删除, event={}, skillId={}, name={}, versionCount={}, operator={}",
                "skill_deleted",
                skillId,
                skill.name().value(),
                artifactKeys.size(),
                actingUser.userId());

        deleteArtifactsBestEffort(artifactKeys, skillId);
    }

    private void deleteArtifactsBestEffort(List<String> artifactKeys, long skillId) {
        for (String artifactKey : artifactKeys) {
            try {
                artifactStorage.delete(artifactKey);
            } catch (RuntimeException e) {
                // 单个文件删不掉不能中断整轮:剩下的文件照删,少留一个是一个。
                log.warn("制品删除失败,已留下孤儿文件, skillId={}, artifactKey={}", skillId, artifactKey, e);
            }
        }
    }

    private Skill changeStatus(long skillId, SkillStatus status, AuthenticatedUser actingUser) {
        Skill skill = skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
        requireOwnerOrAdmin(skill, actingUser);

        skillRepository.updateStatus(skillId, status);

        // 安全事件一律 WARN 起步(backend/CLAUDE.md 6.6):下架 / 恢复直接影响一个 skill 是否还能被公开发现,
        // 值得留一条能被巡检看到的记录,而不是淹没在 DEBUG 里。
        log.warn("skill 状态已变更, skillId={}, status={}, operator={}", skillId, status, actingUser.userId());
        auditLog.info(
                "skill 状态变更, event={}, skillId={}, status={}, operator={}",
                "skill_status_changed",
                skillId,
                status,
                actingUser.userId());
        return requireSkill(skillId);
    }

    private void requireOwnerOrAdmin(Skill skill, AuthenticatedUser actingUser) {
        boolean allowed = actingUser.isAdmin() || actingUser.userId() == skill.owner().userId();
        if (!allowed) {
            throw new AccessDeniedException("只有 owner 或 ADMIN 能操作这个 skill");
        }
    }

    private Skill requireSkill(long skillId) {
        return skillRepository.findDetailById(skillId).orElseThrow(() -> notFound(skillId));
    }

    private ResourceNotFoundException notFound(long skillId) {
        return new ResourceNotFoundException(SKILL_NOT_FOUND_CODE, "skill 不存在, id=" + skillId);
    }

    private ResourceNotFoundException versionNotFound(long skillId, long versionId) {
        return new ResourceNotFoundException(
                SKILL_VERSION_NOT_FOUND_CODE, "skill 版本不存在, skillId=" + skillId + ", versionId=" + versionId);
    }

    private ResourceNotFoundException fileNotFound(long skillId, long versionId) {
        return new ResourceNotFoundException(
                SKILL_VERSION_FILE_NOT_FOUND_CODE, "文件不存在, skillId=" + skillId + ", versionId=" + versionId);
    }

    private int normalizePage(Integer pageParam) {
        if (pageParam == null || pageParam < MIN_PAGE) {
            return DEFAULT_PAGE;
        }
        return pageParam;
    }

    private int normalizeSize(Integer sizeParam) {
        if (sizeParam == null || sizeParam < MIN_SIZE) {
            return DEFAULT_SIZE;
        }
        return Math.min(sizeParam, MAX_SIZE);
    }
}
