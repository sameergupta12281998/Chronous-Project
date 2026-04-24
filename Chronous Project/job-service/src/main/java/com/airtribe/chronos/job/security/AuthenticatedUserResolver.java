package com.airtribe.chronos.job.security;

import com.airtribe.chronos.commons.error.ForbiddenException;
import com.airtribe.chronos.commons.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedUserResolver {

    public UUID requireUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AuthenticatedUser u)) {
            throw new ForbiddenException("Not authenticated");
        }
        return u.userId();
    }
}
