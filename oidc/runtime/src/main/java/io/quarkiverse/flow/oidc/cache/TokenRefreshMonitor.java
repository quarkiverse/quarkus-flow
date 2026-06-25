package io.quarkiverse.flow.oidc.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.oidc.config.FlowOidcConfig;

/**
 * Background monitor that proactively handles cached tokens nearing expiry, so a workflow task never sends a
 * token that is about to expire mid-call. Runs on a single daemon thread at a configurable rate.
 */
@ApplicationScoped
public class TokenRefreshMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(TokenRefreshMonitor.class);

    @Inject
    FlowOidcConfig config;

    @Inject
    TokenCacheRepository repository;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    void start() {
        if (!config.tokenExchange().enabled()) {
            return;
        }
        Duration rateSeconds = config.tokenExchange().monitorRateSeconds();
        scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory());
        scheduler.scheduleAtFixedRate(this::refreshNearingExpiry, rateSeconds.toSeconds(), rateSeconds.toSeconds(),
                TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    void refreshNearingExpiry() {
        try {
            Duration threshold = Duration.ofSeconds(config.tokenExchange().proactiveRefreshSeconds().toSeconds());
            Collection<CachedToken> tokens = repository.getTokensNearingExpiry(threshold);
            tokens.forEach(this::evictToken);
        } catch (RuntimeException e) {
            LOG.warn("Flow OIDC: proactive refresh cycle failed: {}", e.getMessage());
        }
    }

    private void evictToken(CachedToken token) {
        repository.evict(token.key());
        LOG.debug("Flow OIDC: evicted near-expiry token for scheme '{}' (will re-acquire on next use).",
                token.key().authSchemeName());
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "flow-token-refresh-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
