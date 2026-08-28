package com.leetduel.practice.index;

import com.leetduel.practice.ai.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/practice")
@RequiredArgsConstructor
public class PracticeAdminController {

    private final ProblemCatalogIndexer problemCatalogIndexer;
    private final EmbeddingService embeddingService;

    @PostMapping("/reindex")
    public ResponseEntity<Integer> reindex() {
        return ResponseEntity.ok(problemCatalogIndexer.reindexAll());
    }

    @PostMapping("/embeddings/backfill")
    public ResponseEntity<Integer> backfill(@RequestParam(defaultValue = "25") int batchSize) {
        return ResponseEntity.accepted().body(embeddingService.backfill(Math.min(Math.max(batchSize, 1), 100)));
    }
}
