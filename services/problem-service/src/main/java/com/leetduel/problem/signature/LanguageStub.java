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
@Table(name = "language_stubs", schema = "problem")
@Getter
@Setter
@NoArgsConstructor
public class LanguageStub {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "stub_code", nullable = false, columnDefinition = "text")
    private String stubCode;
}
