package com.example.agentcollab.service;

import com.example.agentcollab.domain.Notification;
import com.example.agentcollab.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;

    @Test
    void listsOnlyNotificationsReturnedForTheCurrentUser() {
        Notification own = new Notification(7L, "TASK_ASSIGNED", "TASK", 9L, "title", "content");
        when(repository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(own));

        var result = new NotificationService(repository).list(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).entityId()).isEqualTo(9L);
        verify(repository).findByUserIdOrderByCreatedAtDesc(7L);
        verify(repository, never()).findByUserIdOrderByCreatedAtDesc(8L);
    }

    @Test
    void marksReadIdempotentlyAndRejectsAnotherUsersNotification() {
        Notification notification = new Notification(7L, "TASK_BLOCKER_CREATED", "TASK_BLOCKER", 9L,
                "blocked", "details");
        when(repository.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenReturn(notification);
        var service = new NotificationService(repository);

        service.markRead(7L, 11L);
        var firstReadAt = notification.getReadAt();
        service.markRead(7L, 11L);

        assertThat(firstReadAt).isNotNull();
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
        verify(repository, times(2)).findByIdAndUserId(11L, 7L);
        verify(repository, times(2)).save(notification);
        verify(repository, never()).findByIdAndUserId(11L, 8L);
    }

    @Test
    void deduplicatesRecipientsWhenCreatingAGroupNotification() {
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new NotificationService(repository);

        service.notifyUsers(List.of(7L, 7L, 8L), "TASK_BLOCKER_CREATED", "TASK_BLOCKER", 9L,
                "blocked", "details");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUserId)
                .containsExactly(7L, 8L);
    }
}
