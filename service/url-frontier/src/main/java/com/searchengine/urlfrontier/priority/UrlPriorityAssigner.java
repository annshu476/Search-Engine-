package com.searchengine.urlfrontier.priority;

import org.springframework.stereotype.Component;

/** Assigns the current crawl priority for newly accepted URLs. */
@Component
public class UrlPriorityAssigner {
    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 10;
    public static final int DEFAULT_PRIORITY = 5;

    public int assign() {
        return DEFAULT_PRIORITY;
    }
}
