# 1.5.3 — Correctif spawn (mods tiers)

Patch correctif sur la 1.5.2. Le flag `mob-spawn-list` ne bloquait pas les entités spawnées par des mods via `addFreshEntity()` — pattern courant utilisé notamment pour remplacer des mobs vanilla au vol (ex. Hominid, et tout mod similaire). La commande `/summon` était aussi concernée.

---

## 🐛 Corrections

### `mob-spawn-list` — mobs de mods qui contournaient le blocage
Certains mods spawnent leurs entités en appelant `addFreshEntity()` directement au lieu de passer par le système de spawn standard (`FinalizeSpawnEvent`). ArcadiaGuard n'interceptait ce chemin que pour les `Animal`, pas pour les `Mob` en général.

Un second handler sur `EntityJoinLevelEvent` vérifie désormais la `mob-spawn-list` pour toutes les entités `Mob` entrant dans le monde. Les spawns naturels déjà annulés par `FinalizeSpawnEvent` n'arrivent jamais à cette étape — pas de double traitement.

Cas couverts par ce correctif : mods qui remplacent des spawns vanilla (Hominid, Mutant Monsters-like, etc.), `/summon`, spawn de sbires via `addFreshEntity`.

---

## 📦 Compatibilité

- **NeoForge 1.21.1** (toutes versions 21.1.x)
- **Java 21**
- Client + Serveur (les deux doivent être à jour)
- **Aucun changement de format de données** : upgrade direct depuis 1.5.2, rien à migrer

---
---

# 1.5.3 — Spawn fix (third-party mods)

Bugfix patch on top of 1.5.2. The `mob-spawn-list` flag was not blocking entities spawned by mods via `addFreshEntity()` — a common pattern used to replace vanilla mobs on the fly (e.g. Hominid, and any similar mod). The `/summon` command was also affected.

---

## 🐛 Fixes

### `mob-spawn-list` — mod-spawned mobs bypassing the block
Some mods spawn their entities by calling `addFreshEntity()` directly instead of going through the standard spawn pipeline (`FinalizeSpawnEvent`). ArcadiaGuard was only intercepting this path for `Animal` entities, not for `Mob` in general.

A second handler on `EntityJoinLevelEvent` now checks `mob-spawn-list` for all `Mob` entities entering the world. Spawns already cancelled by `FinalizeSpawnEvent` never reach this step — no double processing.

Cases covered by this fix: mods replacing vanilla spawns (Hominid, Mutant Monsters-like, etc.), `/summon`, minion spawns via `addFreshEntity`.

---

## 📦 Compatibility

- **NeoForge 1.21.1** (all 21.1.x versions)
- **Java 21**
- Client + Server (both must be updated)
- **No data format change**: direct upgrade from 1.5.2, nothing to migrate
