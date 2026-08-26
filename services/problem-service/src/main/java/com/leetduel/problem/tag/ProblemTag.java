package com.leetduel.problem.tag;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "problem_tags", schema = "problem")
@Getter
@NoArgsConstructor
public class ProblemTag {

    @EmbeddedId
    private ProblemTagId id;

    @Column(name = "problem_id", insertable = false, updatable = false)
    private UUID problemId;

    @Column(name = "tag_id", insertable = false, updatable = false)
    private UUID tagId;

    public ProblemTag(UUID problemId, UUID tagId) {
        this.id = new ProblemTagId(problemId, tagId);
        this.problemId = problemId;
        this.tagId = tagId;
    }
}
