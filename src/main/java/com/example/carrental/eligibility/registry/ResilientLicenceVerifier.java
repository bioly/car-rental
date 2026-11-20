package com.example.carrental.eligibility.registry;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decorator that makes any {@link LicenceVerifier} safe to call over an unreliable network, using only the JDK
 * (Release It! stability patterns, see README for why Resilience4j is not used):
 *
 * <ul>
 *   <li><b>Bulkhead</b>: calls run on a small bounded pool + queue; when full we fail fast.</li>
 *   <li><b>Timeout</b>: {@code Future.get(timeout)}, and the running task is cancelled on expiry.</li>
 *   <li><b>Circuit breaker</b>: {@link CircuitBreaker}; stops calling a failing registry for a while.</li>
 *   <li><b>Retry</b> with exponential backoff and jitter, only for transient technical errors. Business answers
 *       (expired, suspended, ...) are normal return values: never retried, never counted as failures.</li>
 * </ul>
 *
 * Any final failure surfaces as {@link LicenceVerificationUnavailableException} (callers fail closed).
 */
@Slf4j
public class ResilientLicenceVerifier implements LicenceVerifier, AutoCloseable {

    private static final AtomicInteger POOL_IDS = new AtomicInteger();

    private final LicenceVerifier delegate;
    private final ResilienceProperties settings;
    private final ThreadPoolExecutor executor;
    private final CircuitBreaker circuitBreaker;

    public ResilientLicenceVerifier(LicenceVerifier delegate, ResilienceProperties settings, Clock clock) {
        this.delegate = delegate;
        this.settings = settings;
        this.circuitBreaker = new CircuitBreaker(settings.failureThreshold(), settings.openDuration(), clock);

        int poolId = POOL_IDS.incrementAndGet();
        AtomicInteger threadIds = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                settings.maxConcurrentCalls(), settings.maxConcurrentCalls(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),
                runnable -> {
                    Thread t = new Thread(runnable, "licence-verifier-" + poolId + "-" + threadIds.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());   // saturated -> RejectedExecutionException -> fail fast
    }

    @Override
    public LicenceRecord verify(String licenceNumber) {
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            if (!circuitBreaker.tryAcquire()) {
                break;   // open circuit: fail fast, and never retry against it
            }
            try {
                LicenceRecord record = callWithTimeout(licenceNumber);
                circuitBreaker.recordSuccess();
                return record;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                circuitBreaker.recordFailure();
                throw unavailable(e);
            } catch (Exception e) {
                circuitBreaker.recordFailure();
                lastFailure = rootOf(e);
                if (!isTransient(lastFailure)) {
                    break;
                }
                if (attempt < settings.maxAttempts() && !pauseBeforeRetry(attempt)) {
                    break;
                }
            }
        }
        throw unavailable(lastFailure);
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.state();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private LicenceRecord callWithTimeout(String licenceNumber) throws Exception {
        Future<LicenceRecord> future = executor.submit(() -> delegate.verify(licenceNumber));
        try {
            return future.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException e) {
            future.cancel(true);
            throw e;
        }
    }

    /** Exponential backoff (x2 per attempt) with +-50% jitter so many callers do not retry in lock-step. */
    private boolean pauseBeforeRetry(int attempt) {
        double jitter = 0.5 + ThreadLocalRandom.current().nextDouble();
        long millis = (long) (settings.retryBackoff().toMillis() * Math.pow(2, attempt - 1) * jitter);
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private LicenceVerificationUnavailableException unavailable(Throwable cause) {
        log.warn("Licence verification unavailable: cause={} circuit={}",
                cause == null ? "circuit-open" : cause.getClass().getSimpleName(), circuitBreaker.state());
        return new LicenceVerificationUnavailableException("Licence registry unavailable", cause);
    }

    private static Throwable rootOf(Exception e) {
        return e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
    }

    /** Worth retrying: the next attempt may succeed. */
    private static boolean isTransient(Throwable t) {
        return t instanceof LicenceServiceException || t instanceof TimeoutException;
    }
}
