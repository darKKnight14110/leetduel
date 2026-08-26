package com.leetduel.problem.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

// Composite key mirrors the composite PK on problem.problem_tags exactly -
// a problem can't carry the same tag twice, enforced by the DB, not just app code.
//
// Explicit @Column names here are required, not decoration: ProblemTag
// also exposes read-only problemId/tagId fields at the top level (see its
// own comment) so ProblemRepository's JPQL can write `pt.problemId`
// instead of `pt.id.problemId`. Both mappings resolve to the same physical
// column, and without an explicit name here, Hibernate's implicit naming
// strategy registers this embedded field under the literal property name
// ("problemId") as its logical column name while the top-level field's
// explicit @Column("problem_id") registers a different logical name for
// the identical physical column - Hibernate 7's stricter duplicate-binding
// check then refuses to boot at all ("contains physical column name
// [problem_id] referred to by multiple logical column names"). Naming both
// explicitly the same string is what makes the two mappings agree.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ProblemTagId implements Serializable {

    @Column(name = "problem_id")
    private UUID problemId;

    @Column(name = "tag_id")
    private UUID tagId;

    public ProblemTagId(UUID problemId, UUID tagId) {
        this.problemId = problemId;
        this.tagId = tagId;
    }
}
