package com.leetduel.user.event;

import com.leetduel.user.profile.UserProfile;
import com.leetduel.user.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCreatedListener {

    private final UserProfileRepository userProfileRepository;

    // RabbitMQ is at-least-once, so this may run more than once for the same
    // userId (redelivery after a crash/ack timeout). existsById() covers the
    // common case cheaply; the DB's PK constraint + this catch cover the rare
    // race where two deliveries overlap - either way, a duplicate delivery
    // never crashes the consumer or creates a second row.
    @RabbitListener(queues = "${leetduel.events.user-created-queue}")
    public void onUserCreated(UserCreatedEvent event) {
        if (userProfileRepository.existsById(event.userId())) {
            log.debug("Profile already exists for user {}, skipping (duplicate delivery)", event.userId());
            return;
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(event.userId());

        try {
            userProfileRepository.save(profile);
        } catch (DataIntegrityViolationException e) {
            log.debug("Profile for user {} created concurrently, skipping", event.userId());
        }
    }
}
