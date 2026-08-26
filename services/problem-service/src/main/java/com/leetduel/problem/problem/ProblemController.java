package com.leetduel.problem.problem;

import com.leetduel.problem.dto.CreateProblemRequest;
import com.leetduel.problem.dto.ProblemDetailDto;
import com.leetduel.problem.dto.ProblemSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Every route here requires a JWT at the Gateway (no public-paths entry for
// /problems) - see the Gateway's application.properties for that decision.
@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProblemService problemService;

    @GetMapping
    public ResponseEntity<Page<ProblemSummaryDto>> list(
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        return ResponseEntity.ok(problemService.listProblems(difficulty, tag, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(problemService.getPublicDetail(id));
    }

    // No admin/RBAC gate yet - any authenticated user can create/delete a
    // problem. No role system exists in this repo yet; named, deferred
    // trade-off, not an oversight.
    @PostMapping
    public ResponseEntity<UUID> create(@Valid @RequestBody CreateProblemRequest request) {
        UUID id = problemService.createProblem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }
}
