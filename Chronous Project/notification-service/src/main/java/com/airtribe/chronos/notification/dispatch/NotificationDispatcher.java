package com.airtribe.chronos.notification.dispatch;

import com.airtribe.chronos.notification.domain.NotificationEntity;

/**
 * Channel-agnostic notification dispatcher. Production deployments wire in email,
 * webhook, push, or other adapters; the development implementation logs to stdout.
 */
public interface NotificationDispatcher {
    void dispatch(NotificationEntity notification);
}
