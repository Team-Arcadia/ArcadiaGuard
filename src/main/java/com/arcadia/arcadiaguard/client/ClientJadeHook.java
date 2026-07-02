package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import java.lang.reflect.Proxy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@OnlyIn(Dist.CLIENT)
public final class ClientJadeHook {

    private static boolean registered = false;

    private ClientJadeHook() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        register();
    }

    public static void register() {
        if (registered || !ModList.get().isLoaded("jade")) return;
        registered = true;
        try {
            ClassLoader loader = ClientJadeHook.class.getClassLoader();
            Class<?> registrationClass = Class.forName("snownee.jade.impl.WailaClientRegistration", false, loader);
            Object registration = registrationClass.getMethod("instance").invoke(null);
            Object callbacks = registrationClass.getField("beforeRenderCallback").get(registration);

            Class<?> callbackInterface = Class.forName("snownee.jade.api.callback.JadeBeforeRenderCallback", false, loader);
            Object callback = Proxy.newProxyInstance(loader, new Class<?>[] { callbackInterface }, (proxy, method, args) -> {
                if ("beforeRender".equals(method.getName())) return ClientJadeOverlayState.isBlocked();
                return defaultValue(method.getReturnType());
            });

            callbacks.getClass().getMethod("add", int.class, Object.class).invoke(callbacks, 1000, callback);
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] Jade overlay callback registered");
        } catch (Throwable t) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to register Jade overlay callback: {}", t.toString());
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }
}
