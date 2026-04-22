package com.arcadia.arcadiaguard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ReflectionHelper} sur des classes de test locales (aucune dep Minecraft).
 * Couvre : invoke public method, invoke static, field access, invoke missing method,
 * intMethod/boolMethod safe casts, null target handling.
 */
class ReflectionHelperTest {

    // --- Classes tests locales ---

    public static class Dummy {
        public String greeting = "hello";
        public int answer = 42;
        public boolean flag = true;

        public String sayHi() { return "hi"; }
        public int add(int a, int b) { return a + b; }
        public Object returnsNull() { return null; }
        public int intFromVoid() { return 7; }
        public boolean boolNoArgs() { return true; }
    }

    public static class StaticHolder {
        public static String STATIC_FIELD = "static-value";
        public static int staticMethod(int x) { return x * 2; }
    }

    // --- invoke instance method ---

    @Test
    void invoke_publicMethod_returnsValue() {
        var r = ReflectionHelper.invoke(new Dummy(), "sayHi", new Class<?>[0]);
        assertTrue(r.isPresent());
        assertEquals("hi", r.get());
    }

    @Test
    void invoke_withArgs() {
        var r = ReflectionHelper.invoke(new Dummy(), "add",
            new Class<?>[]{int.class, int.class}, 3, 4);
        assertTrue(r.isPresent());
        assertEquals(7, r.get());
    }

    @Test
    void invoke_missingMethod_returnsEmpty() {
        var r = ReflectionHelper.invoke(new Dummy(), "doesNotExist", new Class<?>[0]);
        assertTrue(r.isEmpty());
    }

    @Test
    void invoke_nullTarget_returnsEmpty() {
        var r = ReflectionHelper.invoke(null, "sayHi", new Class<?>[0]);
        assertTrue(r.isEmpty());
    }

    @Test
    void invoke_methodReturningNull_isEmpty() {
        // Optional.ofNullable avec null -> empty.
        var r = ReflectionHelper.invoke(new Dummy(), "returnsNull", new Class<?>[0]);
        assertTrue(r.isEmpty());
    }

    // --- invoke static ---

    @Test
    void invokeStatic_publicMethod_returnsValue() {
        var r = ReflectionHelper.invokeStatic(
            StaticHolder.class.getName(), "staticMethod",
            new Class<?>[]{int.class}, 5);
        assertTrue(r.isPresent());
        assertEquals(10, r.get());
    }

    @Test
    void invokeStatic_missingClass_returnsEmpty() {
        var r = ReflectionHelper.invokeStatic(
            "com.nope.DoesNotExist", "foo", new Class<?>[0]);
        assertTrue(r.isEmpty());
    }

    // --- field access ---

    @Test
    void field_instance_returnsValue() {
        var r = ReflectionHelper.field(new Dummy(), "greeting");
        assertTrue(r.isPresent());
        assertEquals("hello", r.get());
    }

    @Test
    void field_missing_returnsEmpty() {
        var r = ReflectionHelper.field(new Dummy(), "no-such-field");
        assertTrue(r.isEmpty());
    }

    @Test
    void field_staticByFqn_returnsValue() {
        var r = ReflectionHelper.field(StaticHolder.class.getName(), "STATIC_FIELD");
        assertTrue(r.isPresent());
        assertEquals("static-value", r.get());
    }

    // --- safe casts ---

    @Test
    void intMethod_returnsIntValue() {
        assertEquals(7, ReflectionHelper.intMethod(new Dummy(), "intFromVoid"));
    }

    @Test
    void intMethod_missingReturnsZero() {
        assertEquals(0, ReflectionHelper.intMethod(new Dummy(), "nope"));
    }

    @Test
    void boolMethod_true() {
        assertTrue(ReflectionHelper.boolMethod(new Dummy(), "boolNoArgs",
            new Class<?>[0]));
    }

    @Test
    void boolMethod_missingReturnsFalse() {
        assertFalse(ReflectionHelper.boolMethod(new Dummy(), "nope",
            new Class<?>[0]));
    }

    // --- Cache coherence (verifie que NOT_FOUND sentinel reste coherent) ---

    @Test
    void repeatedInvoke_sameResult() {
        var target = new Dummy();
        for (int i = 0; i < 3; i++) {
            assertEquals("hi", ReflectionHelper.invoke(target, "sayHi",
                new Class<?>[0]).orElseThrow());
        }
    }

    @Test
    void repeatedMissing_sameResult() {
        // Verifie que le NOT_FOUND sentinel est reutilise sans recasser au 2e appel.
        var target = new Dummy();
        for (int i = 0; i < 3; i++) {
            assertTrue(ReflectionHelper.invoke(target, "missing",
                new Class<?>[0]).isEmpty());
        }
    }
}
