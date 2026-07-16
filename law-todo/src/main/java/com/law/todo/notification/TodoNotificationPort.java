package com.law.todo.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface TodoNotificationPort
{
    void send(NotificationCommand command);

    /** Channel-neutral minimum delivery data; no Todo entity or definition is exposed. */
    record NotificationCommand(@NotBlank @Size(max=128) String idempotencyKey,@NotNull Long todoId,
            @NotNull Long recipientUserId,@NotBlank @Size(max=32) String type,
            @NotBlank @Size(max=200) String title,@Size(max=1000) String content) { }
}
