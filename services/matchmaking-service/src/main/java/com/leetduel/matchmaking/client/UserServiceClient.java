package com.leetduel.matchmaking.client;

import com.leetduel.matchmaking.dto.InternalProfileResponse;
import com.leetduel.matchmaking.exception.ProfileNotFoundException;
import com.leetduel.matchmaking.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

// Blocking RestClient (not WebClient), same reasoning as submission-service's
// ProblemServiceClient: this whole service is plain Servlet/MVC, pulling in
// reactor for one outbound call would be an unjustified reactive island.
// Calls user-service's INTERNAL endpoint directly (not through the
// Gateway), same trust model as every other inter-service call in this repo.
@Component
public class UserServiceClient {

    private final RestClient restClient;

    public UserServiceClient(@Value("${leetduel.user-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    // Called synchronously on every /queue/join - deliberately never trusts
    // a client-supplied ELO. A client lying about its own ELO to get
    // matched against easier opponents is exactly the integrity issue this
    // call closes.
    public int getElo(UUID userId) {
        try {
            InternalProfileResponse response = restClient.get()
                    .uri("/internal/profiles/{userId}", userId)
                    .retrieve()
                    .body(InternalProfileResponse.class);
            if (response == null || response.elo() == null) {
                throw new ProfileNotFoundException("No profile yet for user " + userId);
            }
            return response.elo();
        } catch (RestClientException e) {
            throw new UserServiceUnavailableException("Could not reach user-service for " + userId, e);
        }
    }
}
