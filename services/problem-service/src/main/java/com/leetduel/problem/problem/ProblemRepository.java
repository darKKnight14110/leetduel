package com.leetduel.problem.problem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    // Single query covers all four filter combinations (neither/either/both
    // null) rather than branching between separate finder methods in the
    // service layer - the EXISTS subquery against the problem_tags join
    // table only runs when tagName is non-null (short-circuited by the OR).
    @Query("""
            SELECT p FROM Problem p
            WHERE (:difficulty IS NULL OR p.difficulty = :difficulty)
            AND (:tagName IS NULL OR EXISTS (
                SELECT 1 FROM ProblemTag pt JOIN Tag t ON t.id = pt.tagId
                WHERE pt.problemId = p.id AND t.name = :tagName
            ))
            ORDER BY p.createdAt DESC
            """)
    Page<Problem> search(@Param("difficulty") Difficulty difficulty, @Param("tagName") String tagName, Pageable pageable);

    // ORDER BY random() is O(n log n) full scan+sort - fine at this
    // project's seed-data scale (dozens of problems), NOT the O(1)
    // technique a real large-catalog system would use (e.g. dense integer
    // ids + floor(random()*count)). Flagged explicitly, not presented as
    // "the" solution to random-row selection at scale. Backs
    // matchmaking-service's random problem-per-match selection.
    @Query(value = "SELECT * FROM problem.problems ORDER BY random() LIMIT 1", nativeQuery = true)
    Optional<Problem> findRandom();
}
