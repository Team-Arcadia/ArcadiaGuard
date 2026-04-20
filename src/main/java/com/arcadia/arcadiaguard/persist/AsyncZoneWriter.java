package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes zone I/O tasks on a dedicated daemon thread.
 * All JSON writes MUST go through this class — never write synchronously on the main thread.
 */
public final class AsyncZoneWriter {

    public enum Policy { FAIL_FAST, BLOCK, DROP }

    private BlockingQueue<Runnable> queue;
    private volatile Policy policy = Policy.BLOCK;
    private volatile boolean running = false;
    private Thread thread;

    private final AtomicLong dropped   = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();

    /** Starts the writer thread. Called on server start. */
    public void start() {
        int capacity = ArcadiaGuardConfig.ASYNC_WRITER_CAPACITY.get();
        String policyStr = ArcadiaGuardConfig.ASYNC_WRITER_POLICY.get();
        try { this.policy = Policy.valueOf(policyStr.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Unknown async_writer_policy '{}', defaulting to BLOCK", policyStr);
            this.policy = Policy.BLOCK;
        }
        this.queue   = new LinkedBlockingQueue<>(capacity);
        this.running = true;
        this.thread  = new Thread(this::loop, "arcadiaguard-zone-writer");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Gracefully stops the writer thread, draining remaining tasks. Called on server stop. */
    public void stop() {
        this.running = false;
        if (this.thread != null) {
            this.thread.interrupt();
            try { this.thread.join(10_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (this.thread.isAlive()) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] AsyncZoneWriter thread did not terminate within 10s, {} tasks may be lost", queue.size());
            }
        }
    }

    /**
     * Schedules a write task. Behaviour on full queue depends on configured policy:
     * BLOCK — wait up to 5 s then log error and drop;
     * FAIL_FAST — drop immediately and log error;
     * DROP — drop silently and increment counter.
     */
    public void schedule(Runnable task) {
        if (queue == null) { task.run(); return; }
        switch (policy) {
            case BLOCK -> {
                try {
                    if (!queue.offer(task, 5L, TimeUnit.SECONDS)) {
                        dropped.incrementAndGet();
                        ArcadiaGuard.LOGGER.error(
                            "[ArcadiaGuard] AsyncZoneWriter queue full after 5 s — write dropped. Check disk/threading.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    dropped.incrementAndGet();
                }
            }
            case FAIL_FAST -> {
                if (!queue.offer(task)) {
                    dropped.incrementAndGet();
                    ArcadiaGuard.LOGGER.error(
                        "[ArcadiaGuard] AsyncZoneWriter queue full — write dropped (FAIL_FAST). Data may be lost!");
                }
            }
            case DROP -> {
                if (!queue.offer(task)) dropped.incrementAndGet();
            }
        }
    }

    /** Returns a one-line stats summary for /ag debug stats. */
    public String stats() {
        BlockingQueue<Runnable> q = this.queue;
        int qSize = q != null ? q.size() : 0;
        int capacity = q != null ? qSize + q.remainingCapacity() : 0;
        return String.format("policy=%s  queued=%d/%d  dropped=%d  processed=%d",
            policy, qSize, capacity, dropped.get(), processed.get());
    }

    private void loop() {
        while (this.running || !this.queue.isEmpty()) {
            try {
                Runnable task = this.queue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    task.run();
                    processed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                drainRemaining();
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] AsyncZoneWriter task failed", e);
            }
        }
    }

    private void drainRemaining() {
        Runnable task;
        while ((task = this.queue.poll()) != null) {
            try { task.run(); processed.incrementAndGet(); }
            catch (Exception e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] AsyncZoneWriter drain task failed", e);
            }
        }
    }
}
