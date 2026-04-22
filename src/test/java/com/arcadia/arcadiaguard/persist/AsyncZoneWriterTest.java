package com.arcadia.arcadiaguard.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AsyncZoneWriter} schedule + coalescing logic SANS demarrer le
 * thread (qui dependrait de ArcadiaGuardConfig). Injecte une queue + queue policy
 * via reflection puis valide que les tasks coalescent par cle.
 */
class AsyncZoneWriterTest {

    private static AsyncZoneWriter newWriter(int capacity, AsyncZoneWriter.Policy policy) throws Exception {
        var w = new AsyncZoneWriter();
        Field q = AsyncZoneWriter.class.getDeclaredField("queue");
        q.setAccessible(true);
        q.set(w, new LinkedBlockingQueue<String>(capacity));
        Field p = AsyncZoneWriter.class.getDeclaredField("policy");
        p.setAccessible(true);
        p.set(w, policy);
        return w;
    }

    @Test
    void schedule_unkeyedTasksAllQueued() throws Exception {
        var w = newWriter(10, AsyncZoneWriter.Policy.DROP);
        AtomicInteger c = new AtomicInteger();
        w.schedule(c::incrementAndGet);
        w.schedule(c::incrementAndGet);
        w.schedule(c::incrementAndGet);
        // 3 unkeyed tasks -> 3 entries dans la queue (synthetic keys uniques).
        assertEquals(3, queueSize(w));
    }

    @Test
    void schedule_keyedDuplicateCoalesces() throws Exception {
        var w = newWriter(10, AsyncZoneWriter.Policy.DROP);
        w.schedule("write|overworld|zone1", () -> {});
        w.schedule("write|overworld|zone1", () -> {});
        w.schedule("write|overworld|zone1", () -> {});
        // Meme cle -> 1 entry dans la queue (les 2 suivants coalescent).
        assertEquals(1, queueSize(w));
    }

    @Test
    void schedule_differentKeysAllQueued() throws Exception {
        var w = newWriter(10, AsyncZoneWriter.Policy.DROP);
        w.schedule("write|a", () -> {});
        w.schedule("write|b", () -> {});
        w.schedule("delete|a", () -> {});
        assertEquals(3, queueSize(w));
    }

    @Test
    void schedule_dropPolicyDropsWhenFull() throws Exception {
        var w = newWriter(2, AsyncZoneWriter.Policy.DROP);
        w.schedule("k1", () -> {});
        w.schedule("k2", () -> {});
        w.schedule("k3", () -> {}); // queue full -> drop
        assertEquals(2, queueSize(w));
        assertTrue(w.stats().contains("dropped=1"));
    }

    @Test
    void schedule_failFastDropsAndIncrements() throws Exception {
        var w = newWriter(1, AsyncZoneWriter.Policy.FAIL_FAST);
        w.schedule("k1", () -> {});
        w.schedule("k2", () -> {});
        assertEquals(1, queueSize(w));
        assertTrue(w.stats().contains("dropped=1"));
    }

    @Test
    void stats_formatStable() throws Exception {
        var w = newWriter(10, AsyncZoneWriter.Policy.BLOCK);
        String s = w.stats();
        assertTrue(s.contains("policy="), "stats() doit inclure policy=");
        assertTrue(s.contains("queued="), "stats() doit inclure queued=");
        assertTrue(s.contains("dropped="), "stats() doit inclure dropped=");
        assertTrue(s.contains("processed="), "stats() doit inclure processed=");
        assertTrue(s.contains("coalesced="), "stats() doit inclure coalesced=");
    }

    @Test
    void schedule_lastWriteWins() throws Exception {
        var w = newWriter(10, AsyncZoneWriter.Policy.DROP);
        AtomicInteger executed = new AtomicInteger();
        Runnable r1 = () -> executed.set(1);
        Runnable r2 = () -> executed.set(2);
        Runnable r3 = () -> executed.set(3);
        w.schedule("k", r1);
        w.schedule("k", r2);
        w.schedule("k", r3);
        // La derniere task remplace les precedentes dans pending.
        @SuppressWarnings("unchecked")
        var pending = (java.util.concurrent.ConcurrentHashMap<String, Runnable>)
            getField(w, "pending");
        Runnable last = pending.get("k");
        last.run();
        assertEquals(3, executed.get());
    }

    @Test
    void policy_enumThreeValues() {
        assertEquals(3, AsyncZoneWriter.Policy.values().length);
    }

    // --- Helpers ---

    private static int queueSize(AsyncZoneWriter w) throws Exception {
        @SuppressWarnings("unchecked")
        var q = (java.util.concurrent.BlockingQueue<String>) getField(w, "queue");
        return q.size();
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = AsyncZoneWriter.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
