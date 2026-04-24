package com.airtribe.chronos.notification.web;

import com.airtribe.chronos.commons.error.ForbiddenException;
import com.airtribe.chronos.commons.error.NotFoundException;
import com.airtribe.chronos.commons.security.AuthenticatedUser;
import com.airtribe.chronos.notification.domain.NotificationEntity;
import com.airtribe.chronos.notification.domain.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) { this.repo = repo; }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID owner = currentUser();
        Page<NotificationEntity> result = repo.findByOwnerIdOrderByCreatedAtDesc(owner, PageRequest.of(page, Math.min(size, 100)));
        return new PageResponse<>(
                result.getContent().stream().map(NotificationResponse::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements()
        );
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@PathVariable UUID id) {
        UUID owner = currentUser();
        NotificationEntity n = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + id));
        if (!n.getOwnerId().equals(owner)) {
            throw new ForbiddenException("You don't own this notification");
        }
        return NotificationResponse.of(n);
    }

    private static UUID currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthenticatedUser u)) {
            throw new ForbiddenException("Not authenticated");
        }
        return u.userId();
    }

    public record NotificationResponse(UUID id, UUID jobId, String type, String message,
                                        Instant createdAt, Instant dispatchedAt) {
        public static NotificationResponse of(NotificationEntity n) {
            return new NotificationResponse(n.getId(), n.getJobId(), n.getType(),
                    n.getMessage(), n.getCreatedAt(), n.getDispatchedAt());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {}
}
