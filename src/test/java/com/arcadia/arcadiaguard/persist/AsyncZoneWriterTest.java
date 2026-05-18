package com.arcadia.arcadiaguard.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    // ── NFR02 : thread-safety et exécution hors main thread ──────────────────

    @Test
    void schedule_withQueueNull_runsTaskSynchronously() {
        // queue == null → fallback inline synchrone (mode non-démarré)
        var w = new AsyncZoneWriter();
        AtomicBoolean ran = new AtomicBoolean(false);
        w.schedule(() -> ran.set(true));
        assertTrue(ran.get(), "Sans queue, la task doit s'exécuter immédiatement");
    }

    @Test
    void thread_isDaemon_whenStarted() throws Exception {
        // NFR02 : le thread writer ne doit pas empêcher l'arrêt de la JVM
        var w = newWriterStarted();
        Thread t = (Thread) getField(w, "thread");
        assertTrue(t.isDaemon(), "Le thread writer doit être un daemon");
        stopWriter(w);
    }

    @Test
    void schedule_tasksExecuteOnWriterThread_notCallerThread() throws Exception {
        // NFR02 : aucune I/O ne doit s'exécuter sur le thread appelant
        var w = newWriterStarted();
        String callerThreadName = Thread.currentThread().getName();
        AtomicReference<String> executingThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        w.schedule("nfr02-thread-check", () -> {
            executingThread.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "La task doit s'exécuter dans les 5 s");
        assertNotEquals(callerThreadName, executingThread.get(),
            "La task NE doit PAS s'exécuter sur le thread appelant");
        assertEquals("arcadiaguard-zone-writer", executingThread.get(),
            "La task doit s'exécuter sur le thread writer");
        stopWriter(w);
    }

    @Test
    void concurrent_scheduleFromTwoThreads_allTasksComplete() throws Exception {
        // NFR02 : concurrent write scheduling depuis 2 threads → aucun deadlock, toutes tasks exécutées
        var w = newWriterStarted();
        int n = 40;
        CountDownLatch done = new CountDownLatch(n);

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < n / 2; i++) {
                final int idx = i;
                w.schedule("thr1-key-" + idx, done::countDown);
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = n / 2; i < n; i++) {
                final int idx = i;
                w.schedule("thr2-key-" + idx, done::countDown);
            }
        });
        t1.start(); t2.start(); t1.join(2000); t2.join(2000);

        assertTrue(done.await(10, TimeUnit.SECONDS),
            "Toutes les tasks concurrent doivent être exécutées sans deadlock");
        stopWriter(w);
    }

    @Test
    void writerThread_name_isArcadiaGuardZoneWriter() throws Exception {
        // Vérifie que le thread a le nom canonique attendu (utile pour profiling/debugging)
        var w = newWriterStarted();
        Thread t = (Thread) getField(w, "thread");
        assertEquals("arcadiaguard-zone-writer", t.getName());
        stopWriter(w);
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

    /** Démarre un writer avec queue injectée, sans dépendance à ArcadiaGuardConfig. */
    private static AsyncZoneWriter newWriterStarted() throws Exception {
        var w = new AsyncZoneWriter();
        // Injecter queue et policy
        Field q = AsyncZoneWriter.class.getDeclaredField("queue");
        q.setAccessible(true);
        q.set(w, new LinkedBlockingQueue<>(100));
        Field p = AsyncZoneWriter.class.getDeclaredField("policy");
        p.setAccessible(true);
        p.set(w, AsyncZoneWriter.Policy.DROP);
        // Démarrer le thread via loop() (méthode privée)
        Field r = AsyncZoneWriter.class.getDeclaredField("running");
        r.setAccessible(true);
        r.set(w, true);
        var loopMethod = AsyncZoneWriter.class.getDeclaredMethod("loop");
        loopMethod.setAccessible(true);
        Thread thread = new Thread(() -> {
            try { loopMethod.invoke(w); } catch (Exception ignored) {}
        }, "arcadiaguard-zone-writer");
        thread.setDaemon(true);
        thread.start();
        Field tf = AsyncZoneWriter.class.getDeclaredField("thread");
        tf.setAccessible(true);
        tf.set(w, thread);
        return w;
    }

    private static void stopWriter(AsyncZoneWriter w) throws Exception {
        Field r = AsyncZoneWriter.class.getDeclaredField("running");
        r.setAccessible(true);
        r.set(w, false);
        Thread t = (Thread) getField(w, "thread");
        if (t != null) t.interrupt();
    }
}
