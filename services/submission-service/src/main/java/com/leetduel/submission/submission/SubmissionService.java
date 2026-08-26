package com.leetduel.submission.submission;

import com.leetduel.submission.client.ProblemServiceClient;
import com.leetduel.submission.dto.CreateSubmissionRequest;
import com.leetduel.submission.dto.InternalProblemDetailResponse;
import com.leetduel.submission.dto.SubmissionResponse;
import com.leetduel.submission.exception.SubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemServiceClient problemServiceClient;
    private final SubmissionWriter submissionWriter;

    // Fetches from Problem Service FIRST, outside any DB transaction - the
    // network call must never happen while holding a DB connection open.
    // SubmissionWriter.persist is the actual atomic (submission + outbox
    // row) transaction boundary; see its own comment for why it has to be
    // a separate bean.
    public SubmissionResponse submitCode(UUID userId, CreateSubmissionRequest request) {
        InternalProblemDetailResponse problemDetail = problemServiceClient.getTestCases(request.problemId());
        UUID submissionId = submissionWriter.persist(userId, request, problemDetail);
        return getById(submissionId);
    }

    public SubmissionResponse getById(UUID id) {
        return submissionRepository.findById(id)
                .map(SubmissionResponse::from)
                .orElseThrow(() -> new SubmissionNotFoundException("Submission not found: " + id));
    }
}
