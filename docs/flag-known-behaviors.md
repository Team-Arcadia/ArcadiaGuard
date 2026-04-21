# ArcadiaGuard — Comportements intentionnels des flags

> Document de référence pour les testeurs et administrateurs. Ces comportements ont été **rapportés comme bugs** par des testeurs mais sont en réalité **volontaires par design**. Si tu penses que l'un d'eux est un vrai bug, ouvre une issue avec un cas d'usage précis.

Dernière mise à jour : 2026-04-21

---

## Combat & Dégâts

### `mob_damage` — sens du flag

**Rapporté** : "le flag bloque les dégâts que les mobs INFLIGENT au joueur, alors qu'on attendait qu'il bloque les dégâts que le joueur reçoit des mobs."

**Réalité** : le flag est défini comme **"refuse que les mobs SUBISSENT des dégâts dans la zone"** ([BuiltinFlags.java#L30](../src/main/java/com/arcadia/arcadiaguard/flag/BuiltinFlags.java)). Le nom `mob_damage` = dégâts infligés aux mobs.

**Pour bloquer les dégâts reçus** par le joueur : utiliser `player_damage` ou `invincible`.

---

### `player_damage` + régénération passive en zone

**Rapporté** : "pas besoin du flag `player_damage` — la zone donne déjà `fire_resistance` et `regeneration` automatiquement."

**Réalité** : les effets sont appliqués par `heal_amount` et `feed_amount` (flags int séparés). Si ta zone a ces valeurs > 0, le joueur regagne vie/faim en continu. `player_damage` bloque les dégâts à la source, ce qui est complémentaire.

---

### `attack_animals` / `attack_monsters`

**Rapporté** : "les animaux/monstres prennent toujours les dégâts quand on les attaque."

**Réalité** : le handler annule bien `AttackEntityEvent`. Si des dégâts passent, vérifier :
1. Le testeur est-il OP avec Debug mode **OFF** ? (OP bypass par défaut)
2. Le testeur est-il membre whitelisté de la zone ? (bypass)
3. Le flag est-il bien à `false` (rouge) et non `true` (vert) ?

---

## Blocs

### `block_explosion` n'est PAS un catch-all

**Rapporté** : "TNT et creepers explosent malgré `block_explosion=false`."

**Réalité** : `block_explosion` est le **fallback** pour les explosions "autres" (End crystal, dragon, explosions custom). TNT et creepers ont leurs flags dédiés :
- `tnt_explosion` pour la TNT vanilla
- `creeper_explosion` pour les creepers

Pour tout bloquer, activer les trois simultanément.

---

## Entités

### `mob_bucket` — récupération OK mais pose bloquée

**Rapporté** : "le seau récupère bien le mob mais la pose est bloquée."

**Réalité** : le handler couvre **trois** actions distinctes :
1. Clic droit en l'air avec mob_bucket (release)
2. Clic droit sur eau avec mob_bucket (release in water)
3. Clic droit sur mob Bucketable avec seau vide (capture)

Si le testeur voit "récupération OK, pose bloquée", c'est que le flag `mob_bucket` est à `true` (autorisé) et le flag `block_place` est à `false` (bloque la pose du seau sur un bloc d'eau).

---

## Magie / Mods

### `charm_use` — slots Curios et Accessories ignorés

**Rapporté** : "les charms Apotheosis fonctionnent encore quand équipés dans un slot Curios ou Accessories."

**Réalité** : **par design**. L'intégration `charm_use` cible uniquement les slots Apotheosis natifs (composant data `apotheosis:charm_enabled`). Les slots Curios et Accessories utilisent leur propre API non intégrée. Pour les couvrir, une story future pourrait ajouter un handler Curios dédié.

---

### `tf_projectile` — uniquement projectiles Twilight Forest natifs

**Rapporté** : "l'arc Ice Bow de Twilight Forest tire encore des flèches."

**Réalité** : le filtre actuel ne bloque que les projectiles du namespace `twilightforest`. L'Ice Bow tire des `minecraft:arrow` / `minecraft:spectral_arrow` (namespace vanilla) → elles passent. **Fix planifié en S-H16 T4** : check supplémentaire sur l'owner qui tient un arc TF.

---

### `npc_interact` — uniquement NPCs `easy_npc`

**Rapporté** : "le wandering trader vanilla n'est pas bloqué."

**Réalité** : le flag cible le mod `easy_npc` uniquement. Les villageois/traders vanilla sont gérés par les flags vanilla (`entity_interact` implicite). Si besoin de bloquer le wandering trader, ouvrir une story pour un flag dédié.

---

## GUI

### `gui_dim_flags` — zones cross-dimension

**Rapporté** : "depuis une dimension, on ne voit pas les zones des autres dimensions."

**Corrigé en S-H18** (commit `550e299`). Les zones de toutes les dims sont désormais affichées dans la sidebar, avec les counts corrects et les dimensions modées visibles.

---

## Comment tester correctement

1. **Mode Debug** : toujours activer `/ag gui → Debug` avant de tester un flag — sinon l'OP bypass toutes les protections.
2. **Flags en rouge = OFF = bloqué** : un flag rouge signifie "action interdite". Un flag vert = autorisé.
3. **Whitelist de zone** : si tu es membre whitelisté, tu bypass tous les flags (par design).
4. **Cross-reference avec ce document** avant d'ouvrir un rapport de bug.

---

## Historique des versions

- **2026-04-21** — Création du document suite à l'audit des retours NokhXyr + THE_Fricadelle (2026-04-20).
