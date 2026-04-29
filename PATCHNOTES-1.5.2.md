# 1.5.2 — Correctifs réseau

Patch correctif sur la 1.5.1. Un crash réseau silencieux empêchait la sauvegarde du flag `mob-spawn-list` dès que la liste dépassait 256 caractères, provoquant une déconnexion et faisant réapparaître les mobs à la reconnexion. Six autres problèmes de codec et de threading réseau corrigés dans la foulée.

---

## 🐛 Corrections

### Crash réseau sur `mob-spawn-list` longue — déconnexion + mobs qui réapparaissent
Le codec `ARG2` du packet `gui_action` (C→S) était limité à 256 bytes. Une liste de mobs suffisamment longue dépassait cette limite, faisait échouer l'encodage Netty, déconnectait le joueur, et le flag n'étant jamais reçu par le serveur, les mobs continuaient à spawner après reconnexion.

La limite est portée à 32 767 bytes (limite native Minecraft pour les strings de custom payload).

### `ITEM_BLOCK_ADD` / `ITEM_BLOCK_REMOVE` — ResourceLocations tronquées
Le codec `ARG1` du même packet était limité à 64 bytes — insuffisant pour certains item IDs de mods (ex. `ars_nouveau:manipulation_essence_item_frame_interaction`). Toute tentative d'ajouter un tel item à la liste bloquée d'une zone crashait le packet côté client.

Limite portée à 256 bytes, alignée sur le codec S→C `ZoneDetailPayload.ITEM_ID_C` qui utilisait déjà 128 bytes pour la même donnée.

### `ZoneLogsPayload` — champs de log sans limite explicite
Les quatre champs de `LogLine` (`timestamp`, `player`, `action`, `pos`) utilisaient `ByteBufCodecs.STRING_UTF8` (32 767 bytes par champ, non borné), contrairement à tous les autres payloads du projet. Champs désormais bornés à leurs valeurs réelles : timestamp=32, player=16, action=128, pos=48.

### Lecture des logs de zone sur le thread tick du serveur
`sendZoneLogs` appelait `auditLogger.tail()` (lecture disque synchrone) depuis `enqueueWork`, bloquant le thread principal pendant la lecture du fichier de log. Remplacé par `tailAsync()` qui dispatche sur le pool async et re-entre sur le thread serveur uniquement pour l'envoi du packet.

### `ZoneRemovedPayload` — allocation de codec inline
`ByteBufCodecs.stringUtf8(64)` était instancié à chaque encode et decode au lieu d'une constante `static final`, créant des objets temporaires inutiles. Corrigé par cohérence avec le reste du projet.

### `SelectionPacket` — suppression du dead code
Fichier utilisant l'ancienne API `FriendlyByteBuf` (pré-NeoForge 1.21), non enregistré dans `PacketHandler`, sans aucune référence dans le code. Supprimé.

---

## 📦 Compatibilité

- **NeoForge 1.21.1** (toutes versions 21.1.x)
- **Java 21**
- Client + Serveur (les deux doivent être à jour)
- Tous les mods compat restent optionnels
- **Aucun changement de format de données** : upgrade direct depuis 1.5.1, rien à migrer

---

## 🔗 Liens

- Repo + issues : [github.com/Team-Arcadia/ArcadiaGuard](https://github.com/Team-Arcadia/ArcadiaGuard)
- Release GitHub : [v1.5.2](https://github.com/Team-Arcadia/ArcadiaGuard/releases/tag/v1.5.2)

---
---

# 1.5.2 — Network fixes

Patch release on top of 1.5.1. A silent network crash prevented the `mob-spawn-list` flag from being saved whenever the list exceeded 256 characters, causing a disconnection and making mobs respawn on reconnect. Six other network codec and threading issues fixed alongside.

---

## 🐛 Fixes

### Network crash on long `mob-spawn-list` — disconnect + mobs respawning
The `ARG2` codec of the `gui_action` packet (C→S) was capped at 256 bytes. A mob list long enough to exceed this limit caused Netty encoding to fail, disconnected the player, and since the flag was never received by the server, mobs kept spawning after reconnect.

Limit raised to 32,767 bytes (Minecraft's native limit for custom payload strings).

### `ITEM_BLOCK_ADD` / `ITEM_BLOCK_REMOVE` — truncated ResourceLocations
The `ARG1` codec of the same packet was capped at 64 bytes — not enough for some mod item IDs (e.g. `ars_nouveau:manipulation_essence_item_frame_interaction`). Any attempt to add such an item to a zone's blocked list would crash the packet on the client side.

Limit raised to 256 bytes, consistent with `ZoneDetailPayload.ITEM_ID_C` which already used 128 bytes for the same data in the S→C direction.

### `ZoneLogsPayload` — unbounded log line fields
All four `LogLine` fields (`timestamp`, `player`, `action`, `pos`) used `ByteBufCodecs.STRING_UTF8` (32,767 bytes each, uncapped), unlike every other payload in the project. Fields are now bounded to their real-world values: timestamp=32, player=16, action=128, pos=48.

### Zone log reading on the server tick thread
`sendZoneLogs` was calling `auditLogger.tail()` (synchronous disk I/O) from `enqueueWork`, blocking the main thread during log file reads. Replaced with `tailAsync()` which dispatches to the async pool and re-enters the server thread only to send the packet.

### `ZoneRemovedPayload` — inline codec allocation
`ByteBufCodecs.stringUtf8(64)` was instantiated on every encode and decode instead of a `static final` constant, creating unnecessary temporary objects. Fixed for consistency with the rest of the project.

### `SelectionPacket` — dead code removed
File using the legacy `FriendlyByteBuf` API (pre-NeoForge 1.21), not registered in `PacketHandler`, with no references anywhere in the codebase. Removed.

---

## 📦 Compatibility

- **NeoForge 1.21.1** (all 21.1.x versions)
- **Java 21**
- Client + Server (both must be updated)
- All mod integrations remain optional
- **No data format change**: direct upgrade from 1.5.1, nothing to migrate

---

## 🔗 Links

- Repo + issues: [github.com/Team-Arcadia/ArcadiaGuard](https://github.com/Team-Arcadia/ArcadiaGuard)
- GitHub release: [v1.5.2](https://github.com/Team-Arcadia/ArcadiaGuard/releases/tag/v1.5.2)
