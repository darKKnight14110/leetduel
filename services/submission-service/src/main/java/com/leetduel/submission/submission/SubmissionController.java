package com.leetduel.submission.submission;

import com.leetduel.submission.dto.CreateSubmissionRequest;
import com.leetduel.submission.dto.SubmissionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Every route here requires a JWT at the Gateway (no public-paths entry for
// /submissions). The Gateway injects X-User-Id after verifying the token -
// see JwtAuthWebFilter - so this controller trusts the header rather than
// re-validating a token itself.
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateSubmissionRequest request) {
        SubmissionResponse response = submissionService.submitCode(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(submissionService.getById(id));
    }
}
