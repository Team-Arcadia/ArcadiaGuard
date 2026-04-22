package com.arcadia.arcadiaguard.client;

/** Etat client static pour bloquer les emotes Emotecraft via ClientEmoteEvents.EMOTE_VERIFICATION. */
public final class ClientEmoteBlockedState {

    private ClientEmoteBlockedState() {}

    private static volatile boolean blocked = false;

    public static boolean isBlocked() { return blocked; }

    public static void setBlocked(boolean value) { blocked = value; }
}
