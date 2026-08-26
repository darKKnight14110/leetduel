package com.leetduel.problem.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProblemTagRepository extends JpaRepository<ProblemTag, ProblemTagId> {

    List<ProblemTag> findByProblemId(UUID problemId);

    List<ProblemTag> findByTagId(UUID tagId);
}
