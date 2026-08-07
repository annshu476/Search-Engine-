package com.searchengine.urlfrontier.model.dto;

import com.searchengine.urlfrontier.validator.ValidUrl;
import jakarta.validation.constraints.NotBlank;

/** Request payload for submitting a URL to the frontier. */
public record SubmitUrlRequest(
        @NotBlank(message = "url must not be blank")
        @ValidUrl
        String url
) {
}
