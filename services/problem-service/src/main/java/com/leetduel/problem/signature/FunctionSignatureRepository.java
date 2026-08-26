package com.leetduel.problem.signature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FunctionSignatureRepository extends JpaRepository<FunctionSignature, UUID> {

    Optional<FunctionSignature> findByProblemId(UUID problemId);
}
