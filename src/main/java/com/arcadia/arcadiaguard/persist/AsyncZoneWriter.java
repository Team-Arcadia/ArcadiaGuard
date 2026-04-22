package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes zone I/O tasks on a dedicated daemon thread.
 * All JSON writes MUST go through this class — never write synchronously on the main thread.
 *
 * <p>Supports <b>write coalescing</b>: {@link #schedule(String, Runnable)} with a key replaces any
 * previously queued task for the same key. A burst of N writes to the same zone thus produces
 * only 1 actual disk write (the most recent one). Unkeyed {@link #schedule(Runnable)} remains
 * available for one-shot tasks that must not be coalesced.
 */
public final class AsyncZoneWriter {

    public enum Policy { FAIL_FAST, BLOCK, DROP }

    /** Queue holds KEYS for coalesced tasks, or NULL-key markers for unkeyed tasks. */
    private volatile BlockingQueue<String> queue;
    /** key -> latest task. Unkeyed tasks use a monotonically increasing synthetic key. */
    private final ConcurrentHashMap<String, Runnable> pending = new ConcurrentHashMap<>();
    private final AtomicLong unkeyedSeq = new AtomicLong();

    private volatile Policy policy = Policy.BLOCK;
    private volatile boolean running = false;
    private Thread thread;

    private final AtomicLong dropped   = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();

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
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] AsyncZoneWriter did not stop in time, forcing flush");
                this.thread.interrupt();
            }
        }
    }

    /**
     * Schedules a task without coalescing. Use for one-shot operations (log writes, etc).
     * Each call produces exactly one disk write.
     */
    public void schedule(Runnable task) {
        String syntheticKey = "_u" + unkeyedSeq.incrementAndGet();
        scheduleKeyed(syntheticKey, task);
    }

    /**
     * Schedules a task with a coalescing key. If another task is already pending for the same key,
     * it is <b>replaced</b> by the new one (last-write-wins). A burst of writes to the same zone
     * thus collapses into a single disk write.
     *
     * <p>Use stable keys like "write|dim|zoneName" or "delete|dim|zoneName" so that create-then-delete
     * sequences resolve to the final intended state.
     */
    public void schedule(String key, Runnable task) {
        scheduleKeyed(key, task);
    }

    private void scheduleKeyed(String key, Runnable task) {
        BlockingQueue<String> q = this.queue;
        if (q == null) { task.run(); return; }
        Runnable previous = pending.put(key, task);
        if (previous != null) {
            coalesced.incrementAndGet();
            return; // key already in queue, no need to re-offer
        }
        switch (policy) {
            case BLOCK -> {
                try {
                    if (!q.offer(key, 5L, TimeUnit.SECONDS)) {
                        pending.remove(key);
                        dropped.incrementAndGet();
                        ArcadiaGuard.LOGGER.error(
                            "[ArcadiaGuard] AsyncZoneWriter queue full after 5 s — write dropped. Check disk/threading.");
                    }
                } catch (InterruptedException e) {
                    pending.remove(key);
                    Thread.currentThread().interrupt();
                    dropped.incrementAndGet();
                }
            }
            case FAIL_FAST -> {
                if (!q.offer(key)) {
                    pending.remove(key);
                    dropped.incrementAndGet();
                    ArcadiaGuard.LOGGER.error(
                        "[ArcadiaGuard] AsyncZoneWriter queue full — write dropped (FAIL_FAST). Data may be lost!");
                }
            }
            case DROP -> {
                if (!q.offer(key)) { pending.remove(key); dropped.incrementAndGet(); }
            }
        }
    }

    /** Returns a one-line stats summary for /ag debug stats. */
    public String stats() {
        BlockingQueue<String> q = this.queue;
        int qSize = q != null ? q.size() : 0;
        int capacity = q != null ? qSize + q.remainingCapacity() : 0;
        return String.format("policy=%s  queued=%d/%d  dropped=%d  processed=%d  coalesced=%d",
            policy, qSize, capacity, dropped.get(), processed.get(), coalesced.get());
    }

    private void loop() {
        BlockingQueue<String> q = this.queue;
        if (q == null) return;
        while (this.running || !q.isEmpty()) {
            try {
                String key = q.poll(100, TimeUnit.MILLISECONDS);
                if (key != null) {
                    Runnable task = pending.remove(key);
                    if (task != null) {
                        task.run();
                        processed.incrementAndGet();
                    }
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
        BlockingQueue<String> q = this.queue;
        if (q == null) return;
        String key;
        while ((key = q.poll()) != null) {
            Runnable task = pending.remove(key);
            if (task == null) continue;
            try { task.run(); processed.incrementAndGet(); }
            catch (Exception e) {
                ArcadiaGuard.LOGGER.error("[ArcadiaGuard] AsyncZoneWriter drain task failed", e);
            }
        }
    }
}
