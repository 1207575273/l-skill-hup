package com.landray.skillhub.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.landray.skillhub.domain.exception.InvalidSkillSortException;

class SkillSearchCriteriaTest {

    private SkillSearchCriteria criteria(int page, int size) {
        return new SkillSearchCriteria(null, List.of(), false, null, null, SkillSortOption.LATEST, page, size);
    }

    @Test
    @DisplayName("offset 由 page/size 推导:第 1 页 offset 为 0")
    void should_computeZeroOffset_when_firstPage() {
        assertThat(criteria(1, 20).offset()).isZero();
    }

    @Test
    @DisplayName("offset 由 page/size 推导:第 3 页、每页 20 条,offset 为 40")
    void should_computeOffset_when_thirdPage() {
        assertThat(criteria(3, 20).offset()).isEqualTo(40);
    }

    @Test
    @DisplayName("sort 为 null 时拒绝构造")
    void should_reject_when_sortIsNull() {
        assertThatThrownBy(() -> new SkillSearchCriteria(null, List.of(), false, null, null, null, 1, 20))
                .isInstanceOf(InvalidSkillSortException.class);
    }

    @Test
    @DisplayName("page 小于 1 时拒绝构造")
    void should_reject_when_pageBelowMinimum() {
        assertThatThrownBy(() -> criteria(0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("size 超过 100 时拒绝构造——夹紧动作是 application 层的职责,这里只守合法性")
    void should_reject_when_sizeExceedsMaximum() {
        assertThatThrownBy(() -> criteria(1, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("size 为 0 时拒绝构造")
    void should_reject_when_sizeBelowMinimum() {
        assertThatThrownBy(() -> criteria(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("size 恰好为上限 100 时应被接受")
    void should_accept_when_sizeIsExactlyMaximum() {
        assertThat(criteria(1, 100).size()).isEqualTo(100);
    }
}
