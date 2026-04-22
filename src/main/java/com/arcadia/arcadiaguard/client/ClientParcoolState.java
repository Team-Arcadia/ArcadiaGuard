package com.arcadia.arcadiaguard.client;

/**
 * S-H21 : etat client static indiquant si les actions ParCool sont bloquees
 * pour le joueur local. Mis a jour via {@code ParcoolBlockedPayload} S->C.
 * Consulte par {@code ActionProcessorMixin} cote client.
 */
public final class ClientParcoolState {

    private ClientParcoolState() {}

    private static volatile boolean blocked = false;

    public static boolean isBlocked() { return blocked; }

    public static void setBlocked(boolean value) { blocked = value; }
}
