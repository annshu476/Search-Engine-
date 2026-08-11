package com.searchengine.urlfrontier.priority;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlPriorityAssignerTest {

    @Test
    void assignsDefaultPriorityWithinAllowedRange() {
        int priority = new UrlPriorityAssigner().assign();

        assertThat(priority).isEqualTo(UrlPriorityAssigner.DEFAULT_PRIORITY);
        assertThat(priority).isBetween(UrlPriorityAssigner.MIN_PRIORITY, UrlPriorityAssigner.MAX_PRIORITY);
    }
}
