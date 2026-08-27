package com.leetduel.matchmaking.client;

import com.leetduel.matchmaking.dto.InternalRandomProblemResponse;
import com.leetduel.matchmaking.exception.ProblemServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class ProblemServiceClient {

    private final RestClient restClient;

    public ProblemServiceClient(@Value("${leetduel.problem-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    // Called once per successful pairing, before MatchWriter.persist() -
    // never inside its @Transactional boundary, same ordering rule
    // submission-service follows for ProblemServiceClient.
    public UUID getRandomProblemId() {
        try {
            InternalRandomProblemResponse response = restClient.get()
                    .uri("/internal/problems/random")
                    .retrieve()
                    .body(InternalRandomProblemResponse.class);
            return response.problemId();
        } catch (RestClientException e) {
            throw new ProblemServiceUnavailableException("Could not pick a random problem", e);
        }
    }
}
