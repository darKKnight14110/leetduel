package com.leetduel.problem.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemTagRepository extends JpaRepository<ProblemTag, ProblemTagId> {

    List<ProblemTag> findByProblemId(UUID problemId);

    List<ProblemTag> findByTagId(UUID tagId);

    @Query(value = """
            SELECT pt.problem_id, t.name
            FROM problem.problem_tags pt
            JOIN problem.tags t ON t.id = pt.tag_id
            WHERE pt.problem_id IN (:problemIds)
            ORDER BY pt.problem_id, t.name
            """, nativeQuery = true)
    List<Object[]> findTagNamesByProblemIds(@Param("problemIds") List<UUID> problemIds);
}
