package com.airtribe.chronos.job.web;

import com.airtribe.chronos.commons.correlation.CorrelationIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String corr = req.getHeader(CorrelationIds.HEADER);
        if (corr == null || corr.isBlank()) corr = UUID.randomUUID().toString();
        MDC.put(CorrelationIds.MDC_KEY, corr);
        res.setHeader(CorrelationIds.HEADER, corr);
        try { chain.doFilter(req, res); } finally { MDC.remove(CorrelationIds.MDC_KEY); }
    }
}
