package com.leetduel.problem.internal;

import com.leetduel.problem.dto.InternalProblemDetailDto;
import com.leetduel.problem.dto.InternalRandomProblemDto;
import com.leetduel.problem.problem.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Not routed through the Gateway at all (no leetduel.gateway.routes entry
// covers /internal/**) - unreachable from outside this service's own
// network, which is the entire access control here. Called directly by
// submission-service at submission-create time to snapshot the full,
// hidden-cases-included test suite into the judge job payload.
@RestController
@RequestMapping("/internal/problems")
@RequiredArgsConstructor
public class InternalProblemController {

    private final ProblemService problemService;

    @GetMapping("/{id}/test-cases")
    public ResponseEntity<InternalProblemDetailDto> getTestCases(@PathVariable UUID id) {
        return ResponseEntity.ok(problemService.getInternalDetail(id));
    }

    // Called by matchmaking-service at match-creation time to assign a
    // problem to a newly formed pair.
    @GetMapping("/random")
    public ResponseEntity<InternalRandomProblemDto> getRandom() {
        return ResponseEntity.ok(problemService.getRandomProblem());
    }
}
