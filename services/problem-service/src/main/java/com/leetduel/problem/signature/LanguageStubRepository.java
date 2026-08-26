package com.leetduel.problem.signature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LanguageStubRepository extends JpaRepository<LanguageStub, UUID> {

    List<LanguageStub> findByProblemId(UUID problemId);
}
