package com.leetduel.practice.index;

import com.leetduel.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemCatalogIndexer {

    private final PracticeRepository practiceRepository;
    private final ObjectMapper objectMapper;

    @Value("${leetduel.problem-service.base-url}")
    private String problemServiceBaseUrl;

    @Scheduled(initialDelayString = "${leetduel.practice.index-initial-delay-ms:10000}",
            fixedDelayString = "${leetduel.practice.index-refresh-delay-ms:900000}")
    public void scheduledReindex() {
        try {
            reindexAll();
        } catch (RuntimeException exception) {
            log.warn("Problem catalog refresh failed; existing practice projection remains available");
        }
    }

    public int reindexAll() {
        RestClient client = RestClient.create(problemServiceBaseUrl);
        int imported = 0;
        int page = 0;
        while (true) {
            JsonNode response = client.get().uri(uriBuilder -> uriBuilder.path("/internal/problems/catalog")
                    .queryParam("page", page).queryParam("size", 250).build()).retrieve().body(JsonNode.class);
            if (response == null || !response.path("content").isArray()) {
                break;
            }
            for (JsonNode item : response.path("content")) {
                String problemId = item.path("problemId").asText();
                String slug = item.path("slug").asText();
                String title = item.path("title").asText();
                String description = item.path("description").asText();
                String difficulty = item.path("difficulty").asText();
                List<String> tags = new ArrayList<>();
                for (JsonNode tag : item.path("tags")) {
                    tags.add(tag.asText());
                }
                String hash = hash(title + "\n" + description + "\n" + difficulty + "\n" + String.join(",", tags));
                practiceRepository.upsertProblemDocument(java.util.UUID.fromString(problemId), slug, title,
                        description, difficulty, tags, hash);
                imported++;
            }
            int totalPages = response.path("totalPages").asInt(page + 1);
            page++;
            if (page >= totalPages || response.path("content").size() == 0) {
                break;
            }
        }
        return imported;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash problem document", exception);
        }
    }
}
