# ArcadiaGuard API — Guide d'integration

Ce guide explique comment un mod tiers peut enregistrer ses propres flags de protection et interagir avec le systeme de zones d'ArcadiaGuard.

---

## Prerequis

- NeoForge 1.21.1
- ArcadiaGuard present dans les dependances (compile-only suffit)

```groovy
// build.gradle
dependencies {
    compileOnly files('libs/arcadiaguard-<version>.jar')
}
```

---

## 1. Creer un flag

ArcadiaGuard propose trois types de flags :

### BooleanFlag (allow/deny)

Le plus courant. `true` = autorise, `false` = bloque.

```java
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;

public class MyModFlags {
    // Par defaut true (autorise) — les admins peuvent le passer a false pour bloquer
    public static final BooleanFlag LASER_BEAM = new BooleanFlag(
        "mymod:laser-beam",       // id unique, format kebab-case avec namespace
        true,                     // valeur par defaut
        "Autorise le tir laser"   // description affichee dans le GUI
    );
}
```

### IntFlag

Pour des valeurs numeriques (limites, cooldowns, etc.).

```java
import com.arcadia.arcadiaguard.api.flag.IntFlag;

public static final IntFlag MAX_SUMMONS = new IntFlag(
    "mymod:max-summons",
    3,                          // valeur par defaut
    "Nombre max d'invocations par joueur dans la zone"
);
```

### ListFlag

Pour des listes de valeurs (items, entites, etc.).

```java
import com.arcadia.arcadiaguard.api.flag.ListFlag;

public static final ListFlag BANNED_SPELLS = new ListFlag(
    "mymod:banned-spells",
    "Liste des sorts interdits dans la zone"
);
// Valeur par defaut : liste vide
```

---

## 2. Enregistrer le flag

L'enregistrement doit se faire pendant `FMLCommonSetupEvent` :

```java
import com.arcadia.arcadiaguard.api.ArcadiaGuardAPI;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod("mymod")
public class MyMod {

    public MyMod(IEventBus modBus) {
        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ArcadiaGuardAPI api = ArcadiaGuardAPI.get();
            api.registerFlag(MyModFlags.LASER_BEAM);
            api.registerFlag(MyModFlags.MAX_SUMMONS);
            api.registerFlag(MyModFlags.BANNED_SPELLS);
        });
    }
}
```

> **Important** : l'id du flag doit etre unique. Si un flag avec le meme id existe deja, une `IllegalArgumentException` est levee.

Une fois enregistre, le flag apparait automatiquement dans :
- Le GUI (flag picker, detail de zone, detail de dimension)
- Les commandes (`/arcadiaguard flag set <zone> <flag> <valeur>`)
- Le fichier de sauvegarde JSON

---

## 3. Verifier un flag dans votre code

### Methode simple : checkFlag

```java
import com.arcadia.arcadiaguard.api.ArcadiaGuardAPI;
import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;

public void onPlayerUseLaser(ServerPlayer player, BlockPos pos) {
    ZoneCheckResult result = ArcadiaGuardAPI.get()
        .checkFlag(player, pos, MyModFlags.LASER_BEAM);

    if (result.blocked()) {
        // Action bloquee — la zone s'appelle result.zoneName()
        player.sendSystemMessage(
            Component.literal("Tir laser interdit dans la zone " + result.zoneName())
        );
        return; // annuler l'action
    }

    // Action autorisee — continuer normalement
}
```

`checkFlag` gere automatiquement :
- Le bypass OP / LuckPerms
- La whitelist de la zone
- L'heritage de flags parent/enfant
- Les flags de dimension

### Verifier le bypass seul

```java
if (ArcadiaGuardAPI.get().guardService().shouldBypass(player)) {
    // Le joueur a le droit de bypass (OP ou LuckPerms)
}
```

### Lire la valeur brute d'un flag sur une zone

```java
Optional<IZone> zone = ArcadiaGuardAPI.get().getZone(level, "spawn");
zone.ifPresent(z -> {
    int maxSummons = z.flag(MyModFlags.MAX_SUMMONS);
    List<String> banned = z.flag(MyModFlags.BANNED_SPELLS);
});
```

---

## 4. Ecouter les evenements de zone

ArcadiaGuard publie des evenements sur `NeoForge.EVENT_BUS` :

### FlagChangedEvent

Declenche quand un flag est modifie sur une zone (GUI ou commande).

```java
import com.arcadia.arcadiaguard.api.event.FlagChangedEvent;

NeoForge.EVENT_BUS.addListener((FlagChangedEvent event) -> {
    if (event.flag() == MyModFlags.LASER_BEAM) {
        boolean oldVal = (Boolean) event.oldValue(); // null si pas set avant
        boolean newVal = (Boolean) event.newValue(); // null si reset au defaut
        LOGGER.info("Flag laser modifie sur {} : {} -> {}",
            event.zone().name(), oldVal, newVal);
    }
});
```

### ZoneCreatedEvent / ZoneRemovedEvent

```java
import com.arcadia.arcadiaguard.api.event.ZoneCreatedEvent;
import com.arcadia.arcadiaguard.api.event.ZoneRemovedEvent;

NeoForge.EVENT_BUS.addListener((ZoneCreatedEvent event) -> {
    LOGGER.info("Zone creee : {}", event.zone().name());
});

NeoForge.EVENT_BUS.addListener((ZoneRemovedEvent event) -> {
    LOGGER.info("Zone supprimee : {}", event.zoneName());
});
```

---

## 5. Interface IZone

`IZone` est une vue read-only d'une zone. Methodes disponibles :

| Methode | Type retour | Description |
|---------|-------------|-------------|
| `name()` | `String` | Nom unique de la zone |
| `dimension()` | `String` | Cle de dimension (ex: `minecraft:overworld`) |
| `minX()`, `minY()`, `minZ()` | `int` | Coin minimum (inclusif) |
| `maxX()`, `maxY()`, `maxZ()` | `int` | Coin maximum (inclusif) |
| `priority()` | `int` | Priorite de resolution des conflits |
| `whitelistedPlayers()` | `Set<UUID>` | Joueurs whitelist |
| `flag(Flag<T>)` | `T` | Valeur du flag (explicite ou defaut) |

---

## 6. Conventions de nommage

| Element | Format | Exemple |
|---------|--------|---------|
| Flag id | `namespace:kebab-case` | `mymod:laser-beam` |
| Description | Phrase courte, francais ou anglais | `"Autorise le tir laser"` |
| Namespace | Meme que votre mod id | `mymod` |

---

## Exemple complet

```java
@Mod("mymod")
public class MyMod {

    public static final BooleanFlag TELEPORT = new BooleanFlag(
        "mymod:teleport", true, "Autorise la teleportation magique");

    public MyMod(IEventBus modBus) {
        modBus.addListener(this::setup);
        NeoForge.EVENT_BUS.addListener(this::onTeleport);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ArcadiaGuardAPI.get().registerFlag(TELEPORT));
    }

    private void onTeleport(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ZoneCheckResult result = ArcadiaGuardAPI.get()
            .checkFlag(player, player.blockPosition(), TELEPORT);

        if (result.blocked()) {
            event.setCanceled(true);
            player.sendSystemMessage(
                Component.literal("Teleportation bloquee dans " + result.zoneName()));
        }
    }
}
```
