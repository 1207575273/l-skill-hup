package com.landray.skillhub.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.domain.model.TagName;
import com.landray.skillhub.domain.model.TagWithSkillCount;

/** tag 的仓储接口。只有接口,纯 Java,实现在 {@code infrastructure.persistence}。 */
public interface TagRepository {

    /**
     * 词条列表,附带各词条下可见(PUBLISHED)skill 的数量,实时 GROUP BY 算出。
     *
     * @param kindFilter {@code null} 表示不筛,返回 CURATED 与 FREE 全部
     */
    List<TagWithSkillCount> findAll(TagKind kindFilter);

    /** 按 id 批量查询,用于校验上传/更新请求里给出的 CURATED 标签 id 是否存在、kind 是否真的是 CURATED。 */
    List<Tag> findByIds(Collection<Long> ids);

    /** 按 id 查单条。治理动作(改 / 删)都要先确认目标存在并拿到它当前的 {@code kind}。 */
    Optional<Tag> findById(long tagId);

    /**
     * 按名称精确查找,不限 kind。用于 ADMIN 建 / 改词条时的唯一性预检——{@code uk_tag_name} 仍是最终防线,
     * 这里查一次只是为了把 409 的错误消息说清楚是撞了哪个词条。
     */
    Optional<Tag> findByName(String name);

    /**
     * 输入联想(业务建模文档 5.7:防标签爆炸的主力)。按名称包含匹配,引用多的排前面。
     *
     * @param kindFilter {@code null} 表示 CURATED 与 FREE 都返回
     * @param limit 返回条数上限,由调用方兜底成合理值
     */
    List<TagWithSkillCount> suggest(String keyword, TagKind kindFilter, int limit);

    /**
     * 按名称查找 FREE 词条,不存在则创建。并发下两人同时打同一个新标签,靠 {@code uk_tag_name} 仲裁——命中
     * 冲突时捕获后重查已有行返回,对用户无感(业务建模文档 5.14)。
     */
    Tag findOrCreateFreeTag(String name, long createdByUserId);

    /**
     * 新建词条。名称撞 {@code uk_tag_name} 时抛
     * {@link com.landray.skillhub.shared.exception.ResourceConflictException},不做"已存在就返回旧的"——那是
     * {@link #findOrCreateFreeTag} 的语义,ADMIN 显式建词条时静默复用会让他以为自己刚建的东西被改了。
     */
    Tag create(TagName name, TagKind kind, String description, int sortOrder, long createdByUserId);

    /** 整体更新一个词条的可变字段。{@code kind} 转换的合法性由 application 层先校验,这里只负责写。 */
    void update(long tagId, TagName name, TagKind kind, String description, int sortOrder);

    /**
     * 该词条当前被多少个 skill 引用。<b>含已下架的 skill</b>——删除是不可逆动作,按"可见数"判断会让挂在
     * 下架 skill 上的标签被静默剥掉,而作者重新上架时才发现分类没了。
     */
    long countReferences(long tagId);

    /** 挂着该词条的 skill 名称样本,按名称升序,至多 {@code sampleLimit} 条。用于把"受影响清单"摆给 ADMIN 看。 */
    List<String> findSkillNamesReferencing(long tagId, int sampleLimit);

    /**
     * 挂着该词条、且当前 CURATED 标签数已达 {@code curatedLimit} 的 skill 名称样本。
     *
     * <p>这是 {@code FREE -> CURATED} 转换的拦截依据(业务建模文档 5.7):该词条一旦变成 CURATED,这些 skill
     * 的 CURATED 数就会溢出上限,而数据库一条都拦不住。
     */
    List<String> findSkillNamesAtCuratedLimit(long tagId, int curatedLimit, int sampleLimit);

    /**
     * 把引用从 {@code fromTagId} 迁到 {@code toTagId}:<b>插新行 + 删旧行</b>,不 UPDATE
     * (业务建模文档 4.7:{@code skill_tag} 只增删不修改)。已经挂着目标词条的 skill 直接跳过,不撞
     * {@code uk_skill_tag}。
     *
     * <p>迁移不会让任何 skill 的同类标签数变多:两个词条 kind 相同(由 application 层校验),没挂目标的
     * 是一换一,已挂目标的是净减一。所以这里不需要再查一遍数量上限。
     */
    void migrateReferences(long fromTagId, long toTagId);

    /** 删除该词条的全部 {@code skill_tag} 引用行。 */
    void deleteReferences(long tagId);

    /** 删除词条本身。调用方负责先处理引用(迁移或删除),这里不做级联。 */
    void deleteTag(long tagId);

    /**
     * 仅当该 FREE 标签此刻已无任何 {@code skill_tag} 引用时才删除,单条语句原子完成,不做先查后删
     * (业务建模文档 5.7:替换标签的同一事务内清理本次操作中失去全部引用的 FREE 标签)。对 CURATED 标签、
     * 仍有引用的 FREE 标签调用本方法都是安全的空操作。
     */
    void deleteFreeTagIfOrphaned(long tagId);
}
