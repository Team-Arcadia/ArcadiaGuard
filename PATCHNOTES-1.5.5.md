# 1.5.5 — Audit de fond + cause racine bug pluie

Patch issu d'une review complète du code (4 hunters parallèles). Treize problèmes corrigés, dont une perte de données silencieuse sur les listes d'items bloqués par zone, plusieurs bypass de protection et des couvertures partielles introduites par les patches précédents. Cause racine du bug "mobs spawnent sous la pluie" identifiée et corrigée.

---

## 🔴 Critique — perte de données

### Listes d'items bloqués par zone effacées à chaque modification de zone
Le snapshot construit pour les écritures asynchrones omettait le champ `blockedItems`. Chaque modification d'une autre propriété de la zone (toggle, set flag, whitelist, bornes…) écrasait le fichier disque avec une liste vide. Au prochain redémarrage, toutes les listes d'items bloqués par zone disparaissaient. **Les zones existantes récupèrent leurs items après ce patch uniquement si aucune autre modification n'a été faite entre-temps.**

---

## 🟠 Bypass et couverture incomplète

### Mobs sous la pluie qui contournaient `mob-spawn` / `mob-spawn-list`
Le filet `EntityJoinLevelEvent` ajouté en 1.5.3 ne vérifiait que `mob-spawn-list`. Les flags booléens `mob-spawn`, `monster-spawn`, `animal-spawn` et `villager-spawn` restaient bypassables par les mods qui spawnent via `addFreshEntity()` (Hominid remplaçant un Zombie par un Juggernaut, etc.). C'est la cause racine du bug "des mobs apparaissent dans ma zone pendant un orage" — les remplacements déclenchés par la météo passaient par cette voie. Le filet couvre désormais l'ensemble des flags spawn.

### Bypass `entity.kill()` / `setHealth(0)` sur les flags d'invincibilité
Les flags `INVINCIBLE` et `ANIMAL_INVINCIBLE` ne couvraient que `LivingIncomingDamageEvent`. Une commande `/kill` ou un appel direct `setHealth(0)` (datapack, mod) tuait quand même l'entité. Un listener `LivingDeathEvent` annule désormais la mort et restaure 1 PV pour ces deux flags.

### Warning spam `marked as removed already` (handlers tiers)
`MutantMonstersHandler.onEntityJoin` et `FlagEventHandler.onVehicleJoin` annulaient leurs `EntityJoinLevelEvent` sans discarder l'entité — même cause que le patch 1.5.4 mais sur deux autres handlers oubliés. Spam de warnings et leak temporaire corrigés.

### `LivingFallEvent` polluait l'audit log
Listener enregistré avec `receiveCancelled=true` mais n'avait pas de check `event.isCanceled()`. Si un autre mod (Apotheosis featherweight, Curios feather falling…) avait déjà annulé les dégâts de chute, ArcadiaGuard auditait quand même un faux refus `fall_damage`. Skip ajouté.

### Parent désactivé continuait à transmettre ses flags aux enfants
La résolution descendait dans la chaîne parent même quand le parent était désactivé via `/ag zone toggle`. Les zones enfants se comportaient comme si le parent était toujours actif. Un parent désactivé est maintenant ignoré — l'enfant tombe sur le grand-parent ou les dim flags.

---

## 🟡 Réseau et threading

### `ZoneLogsPayload.zoneName` non borné (oubli 1.5.2)
Le fix 1.5.2 avait borné les champs de chaque ligne de log mais oublié le `zoneName` au top-level du payload, qui restait sur `ByteBufCodecs.STRING_UTF8` (32 KiB). Cap explicite à 64 ajouté pour cohérence.

### `GuiActionPayload.arg2` long sur des actions qui n'en ont pas besoin
`arg2` peut faire jusqu'à 32 KiB pour transporter des listes sérialisées (mob-spawn-list etc.). Mais sur les actions qui n'en ont pas l'usage, le serveur acceptait quand même un blob de 32 KiB. Rejet désormais des `arg2 > 256` chars sauf sur `SET_FLAG_STR` / `SET_DIM_FLAG_STR`.

### API publique `checkFlag` incohérente avec les handlers internes
`ArcadiaGuardAPI.checkFlag()` retournait `allowed` hors zone même quand un dim flag aurait dû dénier. Les mods externes utilisant l'API voyaient un comportement différent des protections natives. Fallback dim ajouté.

### Sauvegarde des dim flags sur le thread tick
Chaque `set` de dim flag depuis le GUI faisait un `fsync` synchrone sur le thread principal. Les écritures passent désormais par `AsyncZoneWriter` avec un snapshot pré-construit côté tick.

### Race entre lecture de logs et rotation à minuit UTC
Sur Windows, `Files.move` du log courant en archive pouvait crash si un `Files.lines` était ouvert en parallèle (panneau Logs de zone). Lock partagé ajouté entre `tail()` et `rotateIfNeeded()`.

### `LogFileManager.queryLog` (code mort dangereux)
Classe non utilisée en production mais conservée pour ses tests, dont la méthode chargeait le log entier en RAM via `Files.readAllLines`. Marquée `@Deprecated` avec doc claire. Retrait dans une release future.

---

## 📦 Compatibilité

- **NeoForge 1.21.1** (toutes versions 21.1.x)
- **Java 21**
- Client + Serveur (les deux doivent être à jour)
- **Aucun changement de format de données** : upgrade direct depuis 1.5.4

---
---

# 1.5.5 — Deep audit + rain spawn root cause

Patch from a complete code review (4 parallel hunters). Thirteen issues fixed, including a silent data loss on per-zone blocked-item lists, several protection bypasses, and partial coverage introduced by previous patches. Root cause of the "mobs spawn during rain" bug identified and fixed.

---

## 🔴 Critical — data loss

### Per-zone blocked-item lists wiped on any zone modification
The snapshot built for async writes was missing the `blockedItems` field. Any modification of another zone property (toggle, set flag, whitelist, bounds…) overwrote the disk file with an empty list. On the next restart, all per-zone blocked-item lists were gone. **Existing zones recover their items after this patch only if no other modification happened in between.**

---

## 🟠 Bypasses and incomplete coverage

### Mobs during rain bypassing `mob-spawn` / `mob-spawn-list`
The `EntityJoinLevelEvent` safety net added in 1.5.3 only checked `mob-spawn-list`. The boolean flags `mob-spawn`, `monster-spawn`, `animal-spawn`, `villager-spawn` remained bypassable by mods spawning via `addFreshEntity()` (Hominid replacing a Zombie with a Juggernaut, etc.). This is the root cause of the "mobs appearing in my zone during a thunderstorm" bug — weather-triggered replacements went through this path. The safety net now covers all spawn flags.

### `entity.kill()` / `setHealth(0)` bypass on invincibility flags
The `INVINCIBLE` and `ANIMAL_INVINCIBLE` flags only covered `LivingIncomingDamageEvent`. A `/kill` command or a direct `setHealth(0)` call (datapack, mod) killed the entity anyway. A `LivingDeathEvent` listener now cancels the death and restores 1 HP for these two flags.

### `marked as removed already` warning spam (third-party handlers)
`MutantMonstersHandler.onEntityJoin` and `FlagEventHandler.onVehicleJoin` cancelled their `EntityJoinLevelEvent` without discarding the entity — same root cause as the 1.5.4 patch but on two other handlers that were missed. Warning spam and temporary leak fixed.

### `LivingFallEvent` polluted the audit log
Listener registered with `receiveCancelled=true` but had no `event.isCanceled()` check. If another mod (Apotheosis featherweight, Curios feather falling…) had already cancelled the fall damage, ArcadiaGuard still logged a false `fall_damage` denial. Skip added.

### Disabled parent kept propagating flags to children
The resolver walked the parent chain even when the parent was disabled via `/ag zone toggle`. Child zones behaved as if the parent were still active. A disabled parent is now skipped — children fall through to the grand-parent or dim flags.

---

## 🟡 Network and threading

### `ZoneLogsPayload.zoneName` unbounded (1.5.2 miss)
The 1.5.2 fix had bounded each log line field but missed the top-level `zoneName` of the payload, which remained on `ByteBufCodecs.STRING_UTF8` (32 KiB). Explicit cap of 64 added for consistency.

### `GuiActionPayload.arg2` long on actions that don't need it
`arg2` can be up to 32 KiB to carry serialized lists (mob-spawn-list etc.). But on actions that don't need it, the server still accepted a 32 KiB blob. `arg2 > 256` chars are now rejected except on `SET_FLAG_STR` / `SET_DIM_FLAG_STR`.

### Public API `checkFlag` inconsistent with internal handlers
`ArcadiaGuardAPI.checkFlag()` returned `allowed` outside zones even when a dim flag should have denied. External mods using the API saw different behaviour from native protections. Dim fallback added.

### Dim flags saved on the tick thread
Every dim flag `set` from the GUI did a synchronous `fsync` on the main thread. Writes now go through `AsyncZoneWriter` with a snapshot pre-built on the tick side.

### Race between log reading and midnight UTC rotation
On Windows, `Files.move` of the current log to archive could crash if a `Files.lines` was open in parallel (zone Logs panel). Shared lock added between `tail()` and `rotateIfNeeded()`.

### `LogFileManager.queryLog` (dangerous dead code)
Class not used in production but kept for its tests, whose method loaded the entire log into RAM via `Files.readAllLines`. Marked `@Deprecated` with clear docs. Removal in a future release.

---

## 📦 Compatibility

- **NeoForge 1.21.1** (all 21.1.x versions)
- **Java 21**
- Client + Server (both must be updated)
- **No data format change**: direct upgrade from 1.5.4
