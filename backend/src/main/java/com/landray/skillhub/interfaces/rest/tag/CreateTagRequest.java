package com.landray.skillhub.interfaces.rest.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /tags} 的请求体。只能建 CURATED,所以没有 {@code kind} 字段(理由见
 * {@code TagGovernanceApplicationService#createCuratedTag})。
 *
 * <p>{@code @Size(max = 32)} 是列宽那一档的<b>粗筛</b>,不是业务上限。真正的 16 字符上限由
 * {@code TagName} 在归一化之后判定——注解量的是原始输入,一个前后各带一个空格的 15 字名字在注解眼里是 17,
 * 按 16 卡就会把合法输入拒掉。所以这里放到列宽,只用来挡明显离谱的超长请求体。
 */
public record CreateTagRequest(
        @NotBlank(message = "标签名不能为空") @Size(max = 32, message = "标签名过长") String name,
        @Size(max = 128, message = "描述不得超过 128 字符") String description,
        Integer sortOrder) {}
