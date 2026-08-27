package com.leetduel.user.event;

import com.leetduel.user.profile.UserProfile;
import com.leetduel.user.profile.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCreatedListenerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Test
    void onUserCreated_createsProfileWithGivenUserId_whenNoneExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserCreatedListener listener = new UserCreatedListener(userProfileRepository);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        // Act
        listener.onUserCreated(new UserCreatedEvent(userId, "alice"));

        // Assert
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void onUserCreated_skipsInsert_whenProfileAlreadyExists() {
        // Arrange - simulates redelivery of the same message (RabbitMQ is
        // at-least-once), which is expected to happen sometimes.
        UUID userId = UUID.randomUUID();
        UserCreatedListener listener = new UserCreatedListener(userProfileRepository);
        UserProfile existing = new UserProfile();
        existing.setUserId(userId);
        existing.setUsername("alice");
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

        // Act
        listener.onUserCreated(new UserCreatedEvent(userId, "alice"));

        // Assert
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void onUserCreated_doesNotThrow_whenConcurrentInsertRacesPastTheExistsCheck() {
        // Arrange - the exists() check said "no row", but by the time save()
        // runs, a concurrent redelivery already inserted one; the DB's PK
        // constraint rejects the duplicate, which the listener must absorb.
        UUID userId = UUID.randomUUID();
        UserCreatedListener listener = new UserCreatedListener(userProfileRepository);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(userProfileRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // Act & Assert - must not propagate, a thrown exception here would
        // nack the message and trigger endless redelivery.
        listener.onUserCreated(new UserCreatedEvent(userId, "alice"));
    }
}
