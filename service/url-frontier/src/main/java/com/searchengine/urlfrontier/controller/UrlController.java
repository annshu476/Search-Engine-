package com.searchengine.urlfrontier.controller;

import com.searchengine.urlfrontier.constant.ApiPaths;
import com.searchengine.urlfrontier.model.dto.SubmitUrlRequest;
import com.searchengine.urlfrontier.model.dto.SubmitUrlResponse;
import com.searchengine.urlfrontier.service.UrlFrontierService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for URL submissions. */
@RestController
@RequestMapping(ApiPaths.URLS)
public class UrlController {

    private final UrlFrontierService urlFrontierService;

    public UrlController(UrlFrontierService urlFrontierService) {
        this.urlFrontierService = urlFrontierService;
    }

    @PostMapping
    public ResponseEntity<SubmitUrlResponse> submit(@Valid @RequestBody SubmitUrlRequest request) {
        SubmitUrlResponse response = urlFrontierService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
