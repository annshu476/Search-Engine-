package com.searchengine.indexer.evaluation;

import com.searchengine.indexer.model.evaluation.EvaluationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/evaluation")
@RequiredArgsConstructor
public class SearchEvaluationController {

    private final SearchEvaluationService evaluationService;

    @PostMapping("/run")
    public ResponseEntity<EvaluationReport> runEvaluation() {
        EvaluationReport report = evaluationService.runBuiltInEvaluation();
        return ResponseEntity.ok(report);
    }
}
