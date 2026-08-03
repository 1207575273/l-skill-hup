package com.landray.skillhub.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.landray.skillhub.domain.model.TagKind;
import com.landray.skillhub.infrastructure.persistence.po.TagRow;

/** tag 只读查询。SQL 在 {@code resources/mapper/TagMapper.xml}(带 join 与可选筛选条件)。 */
@Mapper
public interface TagMapper {

    List<TagRow> selectAll(@Param("kind") TagKind kind);
}
