package com.arcadia.arcadiaguard.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public final class ReflectionHelper {

    private ReflectionHelper() {}

    public static Optional<Object> invoke(Object target, String methodName, Class<?>[] types, Object... args) {
        try {
            Method method = findCompatibleMethod(target.getClass(), methodName, types, args);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Object> invokeStatic(String className, String methodName, Class<?>[] types, Object... args) {
        try {
            Class<?> type = Class.forName(className);
            Method method = findCompatibleMethod(type, methodName, types, args);
            return Optional.ofNullable(method.invoke(null, args));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Object> field(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return Optional.ofNullable(field.get(target));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static int intMethod(Object target, String methodName) {
        return ((Number) invoke(target, methodName, new Class<?>[0]).orElse(0)).intValue();
    }

    public static boolean boolMethod(Object target, String methodName, Class<?>[] types, Object... args) {
        return (Boolean) invoke(target, methodName, types, args).orElse(Boolean.FALSE);
    }

    private static Method findCompatibleMethod(Class<?> type, String methodName, Class<?>[] types, Object... args) throws NoSuchMethodException {
        try {
            return type.getMethod(methodName, types);
        } catch (NoSuchMethodException ignored) {
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (!Modifier.isPublic(method.getModifiers())) continue;
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) continue;
                if (isCompatible(parameterTypes, args)) return method;
            }
            throw new NoSuchMethodException(type.getName() + "#" + methodName);
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
