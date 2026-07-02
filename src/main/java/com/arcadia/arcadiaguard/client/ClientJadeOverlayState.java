package com.arcadia.arcadiaguard.client;

/** Etat client static consulte par le callback Jade beforeRender. */
public final class ClientJadeOverlayState {

    private ClientJadeOverlayState() {}

    private static volatile boolean blocked = false;

    public static boolean isBlocked() { return blocked; }

    public static void setBlocked(boolean value) { blocked = value; }
}
