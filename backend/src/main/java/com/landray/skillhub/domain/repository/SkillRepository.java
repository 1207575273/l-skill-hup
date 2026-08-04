package com.landray.skillhub.domain.repository;

import java.util.List;
import java.util.Optional;

import com.landray.skillhub.domain.model.Certification;
import com.landray.skillhub.domain.model.Skill;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.SkillSearchCriteria;
import com.landray.skillhub.domain.model.SkillSource;
import com.landray.skillhub.domain.model.SkillStatus;
import com.landray.skillhub.domain.model.SkillVersion;
import com.landray.skillhub.shared.model.PageResult;

/**
 * skill 的仓储接口。只有接口,纯 Java,实现在 {@code infrastructure.persistence}。
 *
 * <p>写侧方法(上传管线任务补齐)刻意保持"每个方法只做一件单表操作"的粒度,而不是把"新建 skill + 首个版本"
 * 揉成一个大方法——多表写入的编排(顺序、事务边界、唯一约束冲突后的重试)属于 application 层职责
 * (backend/CLAUDE.md 第一节),仓储只负责把每一步翻译成 SQL。
 */
public interface SkillRepository {

    /** 列表查询,分页 + 筛选 + 排序,返回值不含正文与标签外的重内容(卡片轻量视图)。 */
    PageResult<Skill> search(SkillSearchCriteria criteria);

    /** 详情查询,含最新版本全部字段(正文、认证信息)与全部标签。{@code UNLISTED} 的 skill 同样可查——只是不进列表。 */
    Optional<Skill> findDetailById(long skillId);

    /** 按 name 查详情,用于 precheck 的重名检测与 {@code existing} 字段拼装(业务建模文档 6.2)。 */
    Optional<Skill> findByName(String name);

    /** skill 是否存在,用于版本列表接口在返回空数组前先判断"skill 压根不存在"应返回 404。 */
    boolean existsById(long skillId);

    /** 某个 skill 的全部版本,按发布时间倒序,含各版本认证状态。不含正文(见 {@link SkillVersion} 类注释)。 */
    List<SkillVersion> findVersionsBySkillId(long skillId);

    /**
     * 按 skill + 版本号定位一个版本。认证接口的路径参数是版本号({@code /versions/{version}/certify})而不是
     * versionId,理由同下载接口:版本号是人看得懂、贴在群里也不会指错的标识。
     *
     * <p>{@code WHERE skill_id = ? AND version = ?} 的联合条件同时承担鉴权外的一道边界——不能用 A skill 的 id
     * 配 B skill 的版本号改到别人的认证状态。
     */
    Optional<SkillVersion> findVersionBySkillIdAndVersion(long skillId, String version);

    /**
     * 版本文件预览接口的唯一输入:某个 skill 的某个具体版本对应的 {@code artifact_key}。
     *
     * <p>{@code WHERE id = versionId AND skill_id = skillId} 的联合条件天然保证"versionId 必须真的属于这个
     * skillId"——不能用 A skill 的 id 配 B skill 的 versionId 读到别人的文件。版本不存在与版本存在但属于另一个
     * skill 这两种情况统一返回空,不额外区分,避免向调用方泄漏"这个 id 组合是否存在"的信息。
     */
    Optional<String> findVersionArtifactKey(long skillId, long versionId);

    /**
     * 按 {@code content_digest} 查是否已有内容完全相同的版本(precheck 的"完全相同的包已存在"判定,业务建模
     * 文档 5.5)。只需要 {@code skillId} / {@code version} 两个字段,其余字段不保证被填充。
     */
    Optional<SkillVersion> findVersionByContentDigest(String contentDigest);

    /** 某个 skill 当前最大(即 {@code latest_version_id} 指向的)版本号,尚无版本时返回空(业务建模文档 5.4)。 */
    Optional<String> findLatestVersionString(long skillId);

    /**
     * 插入新 skill 行(不含版本,{@code latest_version_id} 留空)。
     *
     * @return 生成的 skill id
     * @throws com.landray.skillhub.shared.exception.ResourceConflictException {@code uk_skill_name} 冲突时,
     *     code 为 {@code SKILL_NAME_DUPLICATED}——不做先查后插,唯一索引是仲裁者(业务建模文档 5.14)
     */
    long insertSkill(
            SkillName name, String displayName, String description, SkillSource source, long ownerUserId,
            String cardExcerpt);

    /**
     * 插入新 skill_version 行。{@code certification} 恒为 {@code NONE}(业务建模文档 5.8:新版本不继承认证),
     * 由调用方在传入的 {@link SkillVersion} 里保证。
     *
     * @return 生成的 version id
     * @throws com.landray.skillhub.shared.exception.ResourceConflictException {@code uk_skill_version} 冲突时,
     *     code 为 {@code SKILL_VERSION_DUPLICATED}
     */
    long insertVersion(SkillVersion draft);

    /**
     * 带 SemVer 数值序递增条件的 {@code latest_version_id} 更新(业务建模文档 5.4 第 5 条 / 5.14)。比较用
     * PostgreSQL 的数组序({@code string_to_array(version,'.')::int[]}),不是字符串序,否则 {@code "10.0.0"}
     * 会被判定小于 {@code "9.0.0"}。
     *
     * @return 是否真的发生了更新——并发发版时后写者传入较小版本号会返回 {@code false},调用方据此判断是否需要
     *     用自己的版本号重新计算 {@code card_excerpt} 等冗余字段
     */
    boolean updateLatestVersionIfGreater(
            long skillId, long versionId, String version, String description, String cardExcerpt);

    /**
     * 整体替换 skill 的标签集合:删旧插新。数量约束(CURATED 1~3、FREE 至多 10)由调用方在调用前校验——仓储层
     * 只负责翻译成 SQL,不做业务规则判断。
     *
     * <p><b>不在这里做孤儿 FREE 标签清理</b>——那需要判断"这个标签是否失去了全部引用",跨到了 tag 这张表,
     * 由 application 层拿到本方法返回的旧标签集合后,对每个不在新集合里的旧标签调用
     * {@code TagRepository.deleteFreeTagIfOrphaned}(业务建模文档 5.7)。
     *
     * @return 替换前该 skill 关联的标签 id 集合
     */
    List<Long> replaceTags(long skillId, List<Long> tagIds);

    /** 改 displayName / source,不改 name(业务建模文档 5.16:name 一经创建不可修改)。 */
    void updateMetadata(long skillId, String displayName, SkillSource source);

    /**
     * 该 skill 全部版本的 {@code artifact_key}。
     *
     * <p>唯一使用者是物理删除:删库之前必须先把键取出来,否则行没了就再也不知道该删哪些文件,制品会永久残留。
     * <b>调用顺序不能反</b>,这一条在 {@code SkillApplicationService#delete} 里有注释钉住。
     */
    List<String> findVersionArtifactKeys(long skillId);

    /**
     * 物理删除 skill 及其版本 / 表态 / 收藏 / 评论行。<b>不可逆。</b>
     *
     * <p>不含 {@code skill_tag}:标签行由 {@code SkillTagAssignmentService#replaceSkillTags(skillId, [])} 清,
     * 那里同时承载"删掉本次失去全部引用的 FREE 标签"这条规则(业务建模文档 5.7)。规则只写一处,
     * 免得删除路径漏掉孤儿清理,让 {@code /tags/suggest} 把死词条推给用户。
     */
    void deleteSkillCascade(long skillId);

    /**
     * 增量调整 {@code skill.comment_count}(业务建模文档 5.15:追加/移除语义走增量,不重算)。
     *
     * <p>放在 {@code SkillRepository} 而不是 {@code SkillCommentRepository}:它写的是 {@code skill} 表。
     * 仓储按<b>被写的表</b>划分,不按触发它的动作划分 —— 否则每加一个会改 skill 计数的动作,
     * 就多一个能写 skill 表的入口,谁在改这张表就再也说不清了。
     */
    void adjustCommentCount(long skillId, int delta);

    /** 下架 / 恢复,只改 status,不动版本、评论、表态、收藏(业务建模文档 5.15)。 */
    void updateStatus(long skillId, SkillStatus status);

    /**
     * 变更某个版本的认证状态,同时写 {@code certified_by_user_id} 与 {@code certified_at}
     * (业务建模文档 5.8:"每次变更"都要留痕,不只是通过的那次)。
     *
     * <p>{@code certified_at} 取数据库的 {@code now()} 而不是应用侧时间:审计事实要以库为准,应用机器的时钟
     * 漂了就会让"谁先谁后"讲不清。
     *
     * <p><b>只写这一张表的这几列。</b>撤销认证不下架 skill(5.8:两者是不同烈度的处置),所以这里绝不能顺手
     * 去动 {@code skill.status}。
     */
    void updateCertification(long versionId, Certification certification, long operatorUserId, String note);
}
