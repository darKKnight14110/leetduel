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
    // userId (redelivery after a crash/ack timeout). The existing profile is
    // updated only when the additive username projection changes. The DB's PK
    // constraint plus this catch cover the rare race where deliveries overlap.
    @RabbitListener(queues = "${leetduel.events.user-created-queue}")
    public void onUserCreated(UserCreatedEvent event) {
        UserProfile existing = userProfileRepository.findById(event.userId()).orElse(null);
        if (existing != null) {
            if (event.username() != null && !event.username().equals(existing.getUsername())) {
                existing.setUsername(event.username());
                userProfileRepository.save(existing);
            }
            log.debug("Profile already exists for user {}, treating delivery as idempotent", event.userId());
            return;
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(event.userId());
        profile.setUsername(event.username());

        try {
            userProfileRepository.save(profile);
        } catch (DataIntegrityViolationException e) {
            log.debug("Profile for user {} created concurrently, skipping", event.userId());
        }
    }
}
