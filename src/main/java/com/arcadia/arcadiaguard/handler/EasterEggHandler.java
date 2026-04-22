package com.arcadia.arcadiaguard.handler;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Easter egg : message actionbar one-shot a la connexion pour les testeurs v1.5.
 * Le prefix "[Easter Egg]" rend clair que ce n'est pas une feature officielle.
 * Delai de 6 secondes apres login pour laisser le client modde finir son chargement.
 */
public final class EasterEggHandler {

    private static final UUID FRICADELLE = UUID.fromString("724b9ff8-9434-4152-9435-e9aafb62375c");
    private static final UUID NOKHXYR    = UUID.fromString("0d03894e-ced6-4520-bf92-a4a73d3ba502");

    private static final int DELAY_TICKS = 120; // 6s @ 20 TPS

    private static final Map<UUID, List<String>> MESSAGES = Map.of(
        FRICADELLE, List.of(
            "FuFu te cherche partout... planque-toi vite !",
            "FuFu a encore perdu sa Fricadelle",
            "Alerte : FuFu est en mode recherche active",
            "Le bacon veille sur Arcadia",
            "Une Fricadelle sauvage apparait !",
            "Odeur de grillade detectee dans la zone",
            "Chef cuisinier d'Arcadia, bienvenue",
            "FuFu radote : 'mais ou est passee ma Fricadelle ?'",
            "Tu sens ce parfum de bacon ? C'est toi",
            "FuFu a active le sonar a Fricadelle",
            "Briefing Fricadelle : mission infiltration",
            "Le chasseur de Fricadelle approche"
        ),
        NOKHXYR, List.of(
            "Le Nokh rode dans les zones",
            "Chasseur de bugs detecte",
            "Un testeur legendaire vient de spawn",
            "L'ombre de NokhXyr plane sur Arcadia",
            "Mode debug : activation automatique",
            "Les bugs tremblent a ton arrivee",
            "NokhXyr, architecte de chaos",
            "Sonde de stabilite : Nokh detecte"
        )
    );

    /** UUID -> ticks restants avant affichage. */
    private static final Map<UUID, Integer> PENDING = new ConcurrentHashMap<>();

    private EasterEggHandler() {}

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!MESSAGES.containsKey(player.getUUID())) return;
        PENDING.put(player.getUUID(), DELAY_TICKS);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            UUID id = entry.getKey();
            it.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) continue;
            List<String> pool = MESSAGES.get(id);
            String msg = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            Component line = Component.literal("\uD83E\uDD5A [Easter Egg] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(msg).withStyle(ChatFormatting.AQUA));
            player.displayClientMessage(line, true);
        }
    }
}
