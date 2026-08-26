package com.leetduel.submission.client;

import com.leetduel.submission.dto.InternalProblemDetailResponse;
import com.leetduel.submission.exception.ProblemServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

// RestClient (blocking), not WebClient - this whole app is a plain Servlet/
// MVC service everywhere else; pulling in reactor/Netty for one outbound
// call would be an unjustified reactive island. Calls Problem Service's
// INTERNAL endpoint directly (not through the Gateway) - this is a
// service-to-service call on the private network, same trust model as
// Judge Worker's own calls.
@Component
public class ProblemServiceClient {

    private final RestClient restClient;

    public ProblemServiceClient(@Value("${leetduel.problem-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    // Called once, synchronously, at submission-create time - not by Judge
    // Worker at consume time. See the Phase 1 plan for why: keeps the async
    // judging critical path independent of this service's uptime. A failure
    // here fails the submit attempt itself with an honest error, rather
    // than silently queuing a job that can never be judged.
    public InternalProblemDetailResponse getTestCases(UUID problemId) {
        try {
            return restClient.get()
                    .uri("/internal/problems/{id}/test-cases", problemId)
                    .retrieve()
                    .body(InternalProblemDetailResponse.class);
        } catch (RestClientException e) {
            throw new ProblemServiceUnavailableException(
                    "Could not reach problem-service for problem " + problemId, e);
        }
    }
}
