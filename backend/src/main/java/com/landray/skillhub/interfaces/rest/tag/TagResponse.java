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
}
