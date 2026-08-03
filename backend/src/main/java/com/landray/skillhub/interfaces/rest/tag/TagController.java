package com.landray.skillhub.interfaces.rest.tag;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.landray.skillhub.application.TagApplicationService;

/**
 * tag 只读接口:词条列表。匿名可用,不做任何鉴权。
 *
 * <p>不分页——业务建模文档通用约定第 8 条:标签几十条量级,一次全量返回即可,分页反而增加前端复杂度。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagApplicationService tagApplicationService;

    public TagController(TagApplicationService tagApplicationService) {
        this.tagApplicationService = tagApplicationService;
    }

    @GetMapping
    public List<TagResponse> listTags(@RequestParam(required = false) String kind) {
        return tagApplicationService.listTags(kind).stream().map(TagResponse::from).toList();
    }
}
