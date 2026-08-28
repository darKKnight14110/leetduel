package com.leetduel.practice.repository;

import com.leetduel.practice.dto.ExplanationContent;
import com.leetduel.practice.dto.ExplanationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PracticeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public record ProgressCounts(int attemptedCount, int solvedCount) {
    }

    public record RecommendationCandidate(UUID problemId, String slug, String title, String difficulty,
            List<String> tags, double semanticSimilarity, int failures, int attempts) {
    }

    public record ExplanationInput(UUID submissionId, UUID userId, UUID problemId, String sourceCode, String verdict,
            int passed, int total, String diagnostics, String title, String description, String difficulty,
            List<String> tags) {
    }

    public ProgressCounts getProgressCounts(UUID userId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE attempted_count > 0),
                       COUNT(*) FILTER (WHERE solved)
                FROM practice.progress
                WHERE user_id = ?
                """, (rs, rowNum) -> new ProgressCounts(rs.getInt(1), rs.getInt(2)), userId);
    }

    public List<UUID> findProblemIds(UUID userId, boolean solved) {
        return jdbcTemplate.query("""
                SELECT problem_id FROM practice.progress
                WHERE user_id = ? AND solved = ?
                ORDER BY last_attempt_at DESC
                LIMIT 500
                """, (rs, rowNum) -> UUID.fromString(rs.getString("problem_id")), userId, solved);
    }

    public com.leetduel.practice.dto.ProblemProgressResponse getProblemProgress(UUID userId, UUID problemId) {
        return jdbcTemplate.query("""
                SELECT problem_id, attempted_count, solved, last_verdict, last_attempt_at
                FROM practice.progress
                WHERE user_id = ? AND problem_id = ?
                """, (rs, rowNum) -> new com.leetduel.practice.dto.ProblemProgressResponse(
                UUID.fromString(rs.getString("problem_id")), rs.getInt("attempted_count"), rs.getBoolean("solved"),
                rs.getString("last_verdict"), rs.getTimestamp("last_attempt_at").toInstant()), userId, problemId)
                .stream().findFirst().orElse(new com.leetduel.practice.dto.ProblemProgressResponse(
                        problemId, 0, false, null, null));
    }

    public List<String> getWeakTags(UUID userId) {
        return jdbcTemplate.query("""
                SELECT tag, COUNT(*) AS failures
                FROM practice.attempts a
                JOIN practice.problem_documents d ON d.problem_id = a.problem_id
                CROSS JOIN LATERAL jsonb_array_elements_text(d.tags) AS tag
                WHERE a.user_id = ? AND a.verdict <> 'ACCEPTED'
                GROUP BY tag
                ORDER BY failures DESC, tag
                LIMIT 12
                """, (rs, rowNum) -> rs.getString("tag"), userId);
    }

    public List<RecommendationCandidate> findCandidates(UUID userId, String queryVector, int limit) {
        String sql = queryVector == null ? """
                SELECT d.problem_id, d.slug, d.title, d.difficulty, d.tags::text,
                       COALESCE((SELECT COUNT(*) FROM practice.attempts a
                           WHERE a.user_id = ? AND a.problem_id = d.problem_id AND a.verdict <> 'ACCEPTED'), 0) AS failures,
                       COALESCE((SELECT attempted_count FROM practice.progress p
                           WHERE p.user_id = ? AND p.problem_id = d.problem_id), 0) AS attempts,
                       0.0 AS semantic_similarity
                FROM practice.problem_documents d
                WHERE NOT EXISTS (SELECT 1 FROM practice.progress p
                                  WHERE p.user_id = ? AND p.problem_id = d.problem_id AND p.solved)
                ORDER BY d.difficulty, d.slug
                LIMIT ?
                """ : """
                SELECT d.problem_id, d.slug, d.title, d.difficulty, d.tags::text,
                       COALESCE((SELECT COUNT(*) FROM practice.attempts a
                           WHERE a.user_id = ? AND a.problem_id = d.problem_id AND a.verdict <> 'ACCEPTED'), 0) AS failures,
                       COALESCE((SELECT attempted_count FROM practice.progress p
                           WHERE p.user_id = ? AND p.problem_id = d.problem_id), 0) AS attempts,
                       1 - (d.embedding <=> CAST(? AS vector)) AS semantic_similarity
                FROM practice.problem_documents d
                WHERE d.embedding IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM practice.progress p
                                  WHERE p.user_id = ? AND p.problem_id = d.problem_id AND p.solved)
                ORDER BY d.embedding <=> CAST(? AS vector), d.problem_id
                LIMIT ?
                """;
        Object[] args = queryVector == null
                ? new Object[]{userId, userId, userId, limit}
                : new Object[]{userId, userId, queryVector, userId, queryVector, limit};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RecommendationCandidate(
                UUID.fromString(rs.getString("problem_id")), rs.getString("slug"), rs.getString("title"),
                rs.getString("difficulty"), parseTags(rs.getString("tags")),
                rs.getDouble("semantic_similarity"), rs.getInt("failures"), rs.getInt("attempts")), args);
    }

    public void upsertProblemDocument(UUID problemId, String slug, String title, String description, String difficulty,
            List<String> tags, String contentHash) {
        try {
            String tagsJson = objectMapper.writeValueAsString(tags);
            jdbcTemplate.update("""
                    INSERT INTO practice.problem_documents
                        (problem_id, slug, title, description, difficulty, tags, content_hash)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                    ON CONFLICT (problem_id) DO UPDATE SET
                        slug = EXCLUDED.slug,
                        title = EXCLUDED.title,
                        description = EXCLUDED.description,
                        difficulty = EXCLUDED.difficulty,
                        tags = EXCLUDED.tags,
                        embedding = CASE WHEN practice.problem_documents.content_hash <> EXCLUDED.content_hash
                                         THEN NULL ELSE practice.problem_documents.embedding END,
                        embedding_model = CASE WHEN practice.problem_documents.content_hash <> EXCLUDED.content_hash
                                               THEN NULL ELSE practice.problem_documents.embedding_model END,
                        embedded_at = CASE WHEN practice.problem_documents.content_hash <> EXCLUDED.content_hash
                                           THEN NULL ELSE practice.problem_documents.embedded_at END,
                        content_hash = EXCLUDED.content_hash,
                        indexed_at = now()
                    """, problemId, slug, title, description, difficulty, tagsJson, contentHash);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not index problem document " + problemId, exception);
        }
    }

    public record UnembeddedDocument(UUID problemId, String title, String description, String difficulty,
            List<String> tags, String contentHash) {
    }

    public List<UnembeddedDocument> findUnembeddedDocuments(int limit) {
        return jdbcTemplate.query("""
                SELECT problem_id, title, description, difficulty, tags::text, content_hash
                FROM practice.problem_documents
                WHERE embedding IS NULL
                ORDER BY problem_id
                LIMIT ?
                """, (rs, rowNum) -> new UnembeddedDocument(UUID.fromString(rs.getString("problem_id")),
                rs.getString("title"), rs.getString("description"), rs.getString("difficulty"),
                parseTags(rs.getString("tags")), rs.getString("content_hash")), limit);
    }

    public void saveEmbedding(UUID problemId, String vectorLiteral, String model) {
        jdbcTemplate.update("""
                UPDATE practice.problem_documents
                SET embedding = CAST(? AS vector), embedding_model = ?, embedded_at = now()
                WHERE problem_id = ?
                """, vectorLiteral, model, problemId);
    }

    public boolean recordAttempt(UUID submissionId, UUID userId, UUID problemId, String language, String verdict,
            int passed, int total, Instant judgedAt, String sourceCode, String diagnostics) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO practice.attempts
                    (submission_id, user_id, problem_id, language, verdict, test_cases_passed, test_cases_total,
                     judged_at, diagnostics)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (submission_id) DO NOTHING
                """, submissionId, userId, problemId, language, verdict, passed, total, Timestamp.from(judgedAt),
                diagnostics == null ? "" : diagnostics);
        if (inserted == 0) {
            return false;
        }
        boolean accepted = "ACCEPTED".equals(verdict);
        jdbcTemplate.update("""
                INSERT INTO practice.progress
                    (user_id, problem_id, attempted_count, solved, first_solved_at, last_attempt_at,
                     last_verdict, best_passed, best_total)
                VALUES (?, ?, 1, ?, CASE WHEN ? THEN ? ELSE NULL END, ?, ?, ?, ?)
                ON CONFLICT (user_id, problem_id) DO UPDATE SET
                    attempted_count = practice.progress.attempted_count + 1,
                    solved = practice.progress.solved OR EXCLUDED.solved,
                    first_solved_at = COALESCE(practice.progress.first_solved_at, EXCLUDED.first_solved_at),
                    last_attempt_at = EXCLUDED.last_attempt_at,
                    last_verdict = EXCLUDED.last_verdict,
                    best_passed = GREATEST(practice.progress.best_passed, EXCLUDED.best_passed),
                    best_total = GREATEST(practice.progress.best_total, EXCLUDED.best_total)
                """, userId, problemId, accepted, accepted, Timestamp.from(judgedAt), Timestamp.from(judgedAt),
                verdict, passed, total);
        jdbcTemplate.update("""
                INSERT INTO practice.explanation_jobs
                    (id, submission_id, user_id, problem_id, source_code, hint_status, last_error)
                VALUES (?, ?, ?, ?, ?, 'QUEUED', NULL)
                ON CONFLICT (submission_id) DO NOTHING
                """, UUID.randomUUID(), submissionId, userId, problemId, sourceCode);
        return true;
    }

    public ExplanationInput getExplanationInput(UUID submissionId) {
        return jdbcTemplate.query("""
                SELECT e.submission_id, e.user_id, e.problem_id, e.source_code,
                       a.verdict, a.test_cases_passed, a.test_cases_total,
                       a.diagnostics, d.title, d.description, d.difficulty, d.tags::text
                FROM practice.explanation_jobs e
                JOIN practice.attempts a ON a.submission_id = e.submission_id
                LEFT JOIN practice.problem_documents d ON d.problem_id = e.problem_id
                WHERE e.submission_id = ?
                """, (rs, rowNum) -> new ExplanationInput(
                UUID.fromString(rs.getString("submission_id")), UUID.fromString(rs.getString("user_id")),
                UUID.fromString(rs.getString("problem_id")), rs.getString("source_code"), rs.getString("verdict"),
                rs.getInt("test_cases_passed"), rs.getInt("test_cases_total"), rs.getString("diagnostics"),
                rs.getString("title"), rs.getString("description"), rs.getString("difficulty"),
                parseTags(rs.getString("tags"))), submissionId).stream().findFirst().orElse(null);
    }

    public boolean claimHint(UUID submissionId) {
        return jdbcTemplate.update("""
                UPDATE practice.explanation_jobs
                SET hint_status = 'GENERATING', updated_at = now()
                WHERE submission_id = ? AND hint_status IN ('QUEUED', 'RETRYABLE')
                """, submissionId) == 1;
    }

    public void saveHint(UUID submissionId, ExplanationContent content) {
        updateExplanation(submissionId, "hint", "READY", content, null);
    }

    public void failHint(UUID submissionId, String error) {
        jdbcTemplate.update("""
                UPDATE practice.explanation_jobs
                SET hint_status = CASE WHEN retry_count + 1 >= 3 THEN 'FAILED' ELSE 'RETRYABLE' END,
                    retry_count = retry_count + 1, last_error = ?, updated_at = now()
                WHERE submission_id = ?
                """, safeError(error), submissionId);
    }

    public boolean claimWalkthrough(UUID submissionId, UUID userId) {
        return jdbcTemplate.update("""
                UPDATE practice.explanation_jobs
                SET walkthrough_status = 'GENERATING', updated_at = now(), last_error = NULL
                WHERE submission_id = ? AND user_id = ?
                  AND walkthrough_status IN ('NOT_REQUESTED', 'RETRYABLE', 'FAILED')
                """, submissionId, userId) == 1;
    }

    public void saveWalkthrough(UUID submissionId, ExplanationContent content) {
        updateExplanation(submissionId, "walkthrough", "READY", content, null);
    }

    public void failWalkthrough(UUID submissionId, String error) {
        jdbcTemplate.update("""
                UPDATE practice.explanation_jobs
                SET walkthrough_status = CASE WHEN retry_count + 1 >= 3 THEN 'FAILED' ELSE 'RETRYABLE' END,
                    retry_count = retry_count + 1, last_error = ?, updated_at = now()
                WHERE submission_id = ?
                """, safeError(error), submissionId);
    }

    public ExplanationResponse getExplanation(UUID submissionId, UUID userId) {
        return jdbcTemplate.query("""
                SELECT submission_id, hint_status, hint_json::text, walkthrough_status, walkthrough_json::text,
                       retry_count, last_error, updated_at
                FROM practice.explanation_jobs
                WHERE submission_id = ? AND user_id = ?
                """, (rs, rowNum) -> new ExplanationResponse(
                UUID.fromString(rs.getString("submission_id")), rs.getString("hint_status"),
                parseExplanation(rs.getString("hint_json")), rs.getString("walkthrough_status"),
                parseExplanation(rs.getString("walkthrough_json")), rs.getInt("retry_count"),
                rs.getString("last_error"), rs.getTimestamp("updated_at").toInstant()), submissionId, userId)
                .stream().findFirst().orElse(null);
    }

    public void markHintRetryable(UUID submissionId) {
        jdbcTemplate.update("UPDATE practice.explanation_jobs SET hint_status = 'RETRYABLE', updated_at = now() WHERE submission_id = ?",
                submissionId);
    }

    public List<UUID> findRetryableHints(int limit) {
        return jdbcTemplate.query("""
                SELECT submission_id FROM practice.explanation_jobs
                WHERE hint_status IN ('QUEUED', 'RETRYABLE') AND updated_at < now() - INTERVAL '30 seconds'
                ORDER BY updated_at LIMIT ?
                """, (rs, rowNum) -> UUID.fromString(rs.getString("submission_id")), limit);
    }

    public void deleteExpiredSourceCode(int retentionDays) {
        jdbcTemplate.update("""
                UPDATE practice.explanation_jobs
                SET source_code = '', updated_at = now()
                WHERE source_code <> ''
                  AND hint_status IN ('READY', 'FAILED')
                  AND created_at < now() - (? * INTERVAL '1 day')
                """, retentionDays);
    }

    private void updateExplanation(UUID submissionId, String kind, String status, ExplanationContent content,
            String error) {
        try {
            String json = objectMapper.writeValueAsString(content);
            String column = kind.equals("hint") ? "hint_json" : "walkthrough_json";
            String statusColumn = kind.equals("hint") ? "hint_status" : "walkthrough_status";
            jdbcTemplate.update("UPDATE practice.explanation_jobs SET " + column + " = CAST(? AS jsonb), "
                    + statusColumn + " = ?, last_error = ?, updated_at = now() WHERE submission_id = ?",
                    json, status, error, submissionId);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize explanation", exception);
        }
    }

    private ExplanationContent parseExplanation(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ExplanationContent.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String safeError(String error) {
        if (error == null) {
            return "AI provider request failed";
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
