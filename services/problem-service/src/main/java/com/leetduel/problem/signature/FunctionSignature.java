package com.leetduel.problem.signature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "function_signatures", schema = "problem")
@Getter
@Setter
@NoArgsConstructor
public class FunctionSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "problem_id", nullable = false, unique = true)
    private UUID problemId;

    @Column(name = "function_name", nullable = false, length = 100)
    private String functionName;

    @Column(name = "return_type", nullable = false, length = 20)
    private String returnType;
}
