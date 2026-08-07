package com.searchengine.urlfrontier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared application metadata exposed to infrastructure components. */
@ConfigurationProperties(prefix = "application")
public record ApplicationProperties(String name) {
}
