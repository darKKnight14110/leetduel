package com.leetduel.user.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EloHistoryRepository extends JpaRepository<EloHistoryEntry, Long> {

    // Naming mirrors OutboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc's
    // convention elsewhere in this repo. Ascending order - a rating chart
    // reads left-to-right chronologically, unlike a "most recent N" feed.
    List<EloHistoryEntry> findTop200ByUserIdOrderByRecordedAtAsc(UUID userId);
}
