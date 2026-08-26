package com.leetduel.problem.signature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParameterRepository extends JpaRepository<Parameter, UUID> {

    List<Parameter> findByFunctionSignatureIdOrderByOrdinalAsc(UUID functionSignatureId);
}
