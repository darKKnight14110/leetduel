package com.leetduel.user.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMatchCompletionRepository extends JpaRepository<ProcessedMatchCompletion, UUID> {
}
