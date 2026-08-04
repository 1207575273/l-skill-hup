package com.landray.skillhub.interfaces.rest.tag;

import com.landray.skillhub.domain.model.Tag;
import com.landray.skillhub.domain.model.TagWithSkillCount;

/** {@code GET /tags} 的一个词条,{@code skillCount} 是实时 GROUP BY 算出的可见 skill 数。 */
public record TagResponse(Long id, String name, String kind, String description, int sortOrder, long skillCount) {

    public static TagResponse from(TagWithSkillCount item) {
        Tag tag = item.tag();
        return new TagResponse(
                tag.id(), tag.name(), tag.kind().name(), tag.description(), tag.sortOrder(), item.skillCount());
    }

    /**
     * 治理动作(建 / 改)的回显,{@code skillCount} 恒为 0。
     *
     * <p>不为此补一次 GROUP BY:建出来的词条必然零引用;改名 / 改描述不影响引用数,而前端在这两个动作之后
     * 都会重新拉一次列表。<b>宁可给一个明确无意义的 0,也不给一个看起来像真数字的过期值。</b>
     */
    public static TagResponse ofNewTag(Tag tag) {
        return new TagResponse(tag.id(), tag.name(), tag.kind().name(), tag.description(), tag.sortOrder(), 0L);
    }
}
