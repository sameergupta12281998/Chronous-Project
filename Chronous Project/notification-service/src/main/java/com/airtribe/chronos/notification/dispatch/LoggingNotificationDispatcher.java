package com.airtribe.chronos.notification.dispatch;

import com.airtribe.chronos.notification.domain.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationDispatcher implements NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationDispatcher.class);
    @Override
    public void dispatch(NotificationEntity n) {
        log.info("NOTIFY user={} type={} jobId={} : {}", n.getOwnerId(), n.getType(), n.getJobId(), n.getMessage());
    }
}
