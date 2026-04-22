package com.arcadia.arcadiaguard.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe reflection wrapper with Method/Field/Class caching.
 * Lookups are cached on first miss so that hot paths (mod-compat event handlers)
 * do not pay O(N) getMethods() scans on every invocation.
 *
 * <p>A sentinel {@link #NOT_FOUND} is stored to memoize misses — repeated calls
 * with a missing target cost one hashmap lookup.
 *
 * <p>MethodKey now uses a stable signature built from the declared type array only
 * (not from runtime arg types), so the key is computed once per call site rather
 * than rebuilt on each invoke. Call sites that can hold a {@link Method} reference
 * directly should use {@link #cachedMethod(Class, String, Class[])} and then call
 * {@link #invokeCachedMethod(Method, Object, Object...)} to avoid any key overhead.
 */
public final class ReflectionHelper {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ReflectionHelper.class);

    private static final Method NOT_FOUND;
    static {
        try {
            NOT_FOUND = ReflectionHelper.class.getDeclaredMethod("notFoundSentinel");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    @SuppressWarnings("unused")
    private static void notFoundSentinel() {}

    /**
     * Lightweight MethodKey: keyed on owner identity + name + declared param types only.
     * hashCode is pre-computed once at construction.
     */
    private static final class MethodKey {
        private final Class<?> owner;
        private final String name;
        private final Class<?>[] types;
        private final int hash;

        private MethodKey(Class<?> owner, String name, Class<?>[] types) {
            this.owner = owner;
            this.name = name;
            this.types = types;
            this.hash = 31 * (31 * System.identityHashCode(owner) + name.hashCode())
                + Arrays.hashCode(types);
        }

        static MethodKey of(Class<?> owner, String name, Class<?>[] types) {
            return new MethodKey(owner, name, types == null ? new Class<?>[0] : types);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodKey k)) return false;
            return owner == k.owner && name.equals(k.name) && Arrays.equals(types, k.types);
        }
    }

    private static final ConcurrentHashMap<MethodKey, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private ReflectionHelper() {}

    // ── Public API ──────────────────────────────────────────────────────────────

    public static Optional<Object> invoke(Object target, String methodName, Class<?>[] types, Object... args) {
        if (target == null) return Optional.empty();
        try {
            Method method = cachedMethod(target.getClass(), methodName, types);
            if (method == null) return Optional.empty();
            return Optional.ofNullable(method.invoke(target, args));
        } catch (InvocationTargetException e) {
            LOG.debug("ReflectionHelper.invoke({}, {}) threw", target.getClass().getSimpleName(), methodName, e.getCause());
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Object> invokeStatic(String className, String methodName, Class<?>[] types, Object... args) {
        try {
            Class<?> type = cachedClass(className);
            if (type == null) return Optional.empty();
            Method method = cachedMethod(type, methodName, types);
            if (method == null) return Optional.empty();
            return Optional.ofNullable(method.invoke(null, args));
        } catch (InvocationTargetException e) {
            LOG.debug("ReflectionHelper.invokeStatic({}.{}) threw", className, methodName, e.getCause());
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Invokes a pre-resolved Method directly, bypassing any key construction.
     * Use this at call sites that cache the Method in a static/instance field.
     */
    public static Optional<Object> invokeCachedMethod(Method m, Object target, Object... args) {
        if (m == null || target == null) return Optional.empty();
        try {
            return Optional.ofNullable(m.invoke(target, args));
        } catch (InvocationTargetException e) {
            LOG.debug("ReflectionHelper.invokeCachedMethod({}) threw", m.getName(), e.getCause());
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Object> field(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        try {
            Field field = cachedField(target.getClass(), fieldName);
            if (field == null) return Optional.empty();
            return Optional.ofNullable(field.get(target));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Reads a static field by FQN (e.g. "com.foo.Bar" + "BAZ"). Cached. */
    public static Optional<Object> field(String className, String fieldName) {
        try {
            Class<?> type = cachedClass(className);
            if (type == null) return Optional.empty();
            Field field = cachedField(type, fieldName);
            if (field == null) return Optional.empty();
            return Optional.ofNullable(field.get(null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** H9: safe cast via instanceof Number to avoid ClassCastException on unexpected types. */
    public static int intMethod(Object target, String methodName) {
        Object v = invoke(target, methodName, new Class<?>[0]).orElse(0);
        return (v instanceof Number n) ? n.intValue() : 0;
    }

    /** H9: safe cast via instanceof Boolean. */
    public static boolean boolMethod(Object target, String methodName, Class<?>[] types, Object... args) {
        Object v = invoke(target, methodName, types, args).orElse(Boolean.FALSE);
        return (v instanceof Boolean b) && b;
    }

    // ── Public cache helper — allows call sites to cache the Method themselves ──

    /**
     * Returns the cached (or newly resolved) Method for the given owner+name+types,
     * or {@code null} if not found. Call sites may store this reference in a
     * {@code static volatile} field to avoid any key construction on the hot path.
     */
    public static Method cachedMethod(Class<?> ownerClass, String name, Class<?>... paramTypes) {
        MethodKey key = MethodKey.of(ownerClass, name, paramTypes);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached == NOT_FOUND ? null : cached;
        Method resolved = resolve(ownerClass, name, paramTypes == null ? new Class<?>[0] : paramTypes,
            /* dummy args */ new Object[paramTypes == null ? 0 : paramTypes.length]);
        METHOD_CACHE.put(key, resolved == null ? NOT_FOUND : resolved);
        return resolved;
    }

    // ── internal cache helpers ──────────────────────────────────────────────────

    private static Class<?> cachedClass(String name) {
        Class<?> c = CLASS_CACHE.computeIfAbsent(name, n -> {
            try { return Class.forName(n); } catch (ClassNotFoundException e) { return Void.class; }
        });
        return c == Void.class ? null : c;
    }

    private static final Field SENTINEL_FIELD;
    static {
        try {
            SENTINEL_FIELD = ReflectionHelper.class.getDeclaredField("METHOD_CACHE");
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Field cachedField(Class<?> owner, String fieldName) {
        String key = owner.getName() + "#" + fieldName;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached == SENTINEL_FIELD ? null : cached;
        try {
            Field f = owner.getField(fieldName);
            FIELD_CACHE.put(key, f);
            return f;
        } catch (NoSuchFieldException e) {
            FIELD_CACHE.put(key, SENTINEL_FIELD);
            return null;
        }
    }

    private static Method resolve(Class<?> owner, String name, Class<?>[] types, Object[] args) {
        try {
            return owner.getMethod(name, types);
        } catch (NoSuchMethodException ignored) {
            for (Method method : owner.getMethods()) {
                if (!method.getName().equals(name)) continue;
                if (!Modifier.isPublic(method.getModifiers())) continue;
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) continue;
                if (isCompatible(parameterTypes, args)) return method;
            }
            return null;
        }
    }

    private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            Class<?> parameterType = wrap(parameterTypes[i]);
            if (arg == null) {
                if (parameterType.isPrimitive()) return false;
                continue;
            }
            if (!parameterType.isAssignableFrom(wrap(arg.getClass()))) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

}
