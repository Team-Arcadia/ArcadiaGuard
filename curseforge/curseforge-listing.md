# CurseForge Project Setup — ArcadiaGuard

---

## Project Settings

**Project Name:** ArcadiaGuard

**Summary:** (max 150 chars)
> Standalone zone protection mod with in-game GUI, wand tool, 60+ configurable flags, 3D zone rendering, and third-party mod integration.

**Main Category:** Server Utility

**Additional Categories:** Management, API and Library

**Game Version:** Minecraft 1.21.1

**Mod Loader:** NeoForge

**Environment:** Client and Server (required on both sides)

**License:** MIT

**Source URL:** https://github.com/Team-Arcadia/ArcadiaGuard

**Issues URL:** https://github.com/Team-Arcadia/ArcadiaGuard/issues

---

## Logo

File: `logo.png` (to be created — 400x400 recommended, PNG)

Suggested design: copper/verdigris shield icon on dark background, matching the Cartographia UI theme (palette: #D89255 copper, #6BA894 verdigris, #17120D dark bg).

---

## Description (English)

```
# ArcadiaGuard

**Standalone zone protection mod for NeoForge 1.21.1** — define protected zones with an in-game GUI, a wand selection tool, 60+ configurable flags, and 3D zone boundary rendering.

---

## Features

### Full In-Game GUI
Everything is managed from the GUI — no commands needed for daily use:
- **Zone List** — tabular view with dimension filters, search, pagination, and live preview
- **Zone Detail** — configure flags, manage members, edit coordinates, view audit logs
- **Flag Picker** — add/remove flags with search, toggle booleans, edit int/list values
- **Dimension Editor** — set default flags per dimension
- **Zone Creation** — form with real-time stats (surface, volume, chunks)

### Wand Selection Tool
- **Edit Mode**: left click = corner A, right click = corner B
- **View Mode**: passive 3D wireframe rendering of zone boundaries
- HUD display of selected positions above the XP bar

### 60+ Protection Flags
Granular control over:
- Block interactions (break, place, interact, containers)
- PvP, entity damage, mob spawning
- Spells and magic abilities (per mod)
- Item usage (leads, spawn eggs, books, dynamic blocking)
- Fluid/fire/explosion propagation
- And more — extensible via the public API

### Sub-Zones & Inheritance
- Create child zones inside parent zones
- Flags inherit from parent to child automatically
- Dimension-level flag defaults
- Conflict resolution: priority > volume > name

### Mod Compatibility
Each integration is independent and toggleable:

| Mod | What's Blocked |
|-----|----------------|
| Iron's Spells 'n Spellbooks | Spell casting |
| Ars Nouveau | Spells, warp scroll teleportation, portals |
| Simply Swords | Special weapon abilities |
| Occultism | Rituals and magic |
| Supplementaries | Throwable items |
| Apotheosis | Tunneling, Enlightened affix |
| Better Archeology | Tunneling enchantment |

All mods are **optional** — ArcadiaGuard works regardless of which mods are installed.

### Dynamic Item Blocking
Block any item in protected zones at runtime via command — no restart needed:
```
/arcadiaguard item block <item>
/arcadiaguard item unblock <item>
```

### Audit Logging
Every blocked action is logged with player name, action type, zone, and position. Daily log rotation with configurable retention.

### LuckPerms Integration
Zone roles (MEMBER, MODERATOR, OWNER) can be assigned via LuckPerms permission nodes — no in-game whitelist management required.

### Public API
Third-party mods can register custom flags and query zone permissions:
```java
ArcadiaGuardAPI.get().registerFlag(
    new BooleanFlag("mymod:teleport", true, "Allow teleportation"));

ZoneCheckResult result = ArcadiaGuardAPI.get()
    .checkFlag(player, pos, MY_FLAG);
```
Full API guide: [docs/api-guide.md](https://github.com/Team-Arcadia/ArcadiaGuard/blob/standalone/docs/api-guide.md)

### Datapack Extensibility
Item lists are driven by overridable tags — no recompilation needed:
- `arcadiaguard:banned_leads`
- `arcadiaguard:banned_spawn_eggs`
- `arcadiaguard:spawn_banned_books`

---

## Installation

1. Download the `.jar` from the Files tab
2. Place it in the `mods/` folder (**server AND client**)
3. Start the server to generate config
4. Give yourself the wand: `/give @s arcadiaguard:zone_editor`
5. Select two corners and open the GUI to create your first zone

**Requires:** NeoForge 1.21.1 (any 21.1.x version) — Java 21

---

## Commands

> Most actions are available directly from the GUI. Commands remain available for automation and console use.

```
/arcadiaguard gui
/arcadiaguard zone add <name> <x1> <y1> <z1> <x2> <y2> <z2>
/arcadiaguard zone remove <name>
/arcadiaguard flag set <zone> <flag> <value>
/arcadiaguard flag list
/arcadiaguard item block <item>
/arcadiaguard reload
/arcadiaguard debug
```

---

## Links

- [Source Code](https://github.com/Team-Arcadia/ArcadiaGuard)
- [API Guide](https://github.com/Team-Arcadia/ArcadiaGuard/blob/standalone/docs/api-guide.md)
- [Issues](https://github.com/Team-Arcadia/ArcadiaGuard/issues)
```

---

## Description (Francais)

```
# ArcadiaGuard

**Mod de protection de zones standalone pour NeoForge 1.21.1** — definissez des zones protegees avec une interface graphique en jeu, un outil de selection (wand), plus de 60 flags configurables et un rendu 3D des limites de zones.

---

## Fonctionnalites

### Interface graphique complete
Tout se gere depuis le GUI — aucune commande necessaire pour l'utilisation courante :
- **Liste des zones** — vue tabulaire avec filtres par dimension, recherche, pagination et apercu
- **Detail de zone** — configuration des flags, gestion des membres, edition des coordonnees, consultation des logs
- **Flag picker** — ajout/suppression de flags avec recherche, toggle des booleens, editeur int/liste
- **Editeur de dimension** — flags par defaut par dimension
- **Creation de zone** — formulaire avec stats en temps reel (surface, volume, chunks)

### Wand de selection
- **Mode Edit** : clic gauche = coin A, clic droit = coin B
- **Mode View** : rendu 3D passif des limites de zones en wireframe
- Affichage HUD des positions au-dessus de la barre d'XP

### 60+ flags de protection
Controle granulaire sur :
- Interactions blocs (casse, pose, interaction, conteneurs)
- PvP, degats d'entites, spawn de mobs
- Sorts et capacites magiques (par mod)
- Utilisation d'items (laisses, oeufs de spawn, livres, blocage dynamique)
- Propagation de fluides, feu, explosions
- Et plus encore — extensible via l'API publique

### Sous-zones et heritage
- Creez des zones enfants a l'interieur de zones parentes
- Heritage automatique des flags parent vers enfant
- Flags par defaut au niveau dimension
- Resolution des conflits : priorite > volume > nom

### Compatibilite mods
Chaque integration est independante et desactivable :

| Mod | Ce qui est bloque |
|-----|-------------------|
| Iron's Spells 'n Spellbooks | Lancement de sorts |
| Ars Nouveau | Sorts, teleportation warp scroll, portails |
| Simply Swords | Capacites speciales des armes |
| Occultism | Rituels et magie |
| Supplementaries | Lancer d'objets |
| Apotheosis | Tunneling, affix Enlightened |
| Better Archeology | Enchantement Tunneling |

Tous les mods sont **optionnels** — ArcadiaGuard fonctionne quel que soit l'environnement.

### Blocage dynamique d'items
Bloquez n'importe quel item en zone protegee en temps reel via commande — sans redemarrage :
```
/arcadiaguard item block <item>
/arcadiaguard item unblock <item>
```

### Logs d'audit
Chaque action bloquee est enregistree avec le nom du joueur, le type d'action, la zone et la position. Rotation journaliere des logs.

### Integration LuckPerms
Les roles de zone (MEMBER, MODERATOR, OWNER) peuvent etre attribues via les noeuds de permission LuckPerms.

### API publique
Les mods tiers peuvent enregistrer des flags custom et verifier les permissions de zone :
```java
ArcadiaGuardAPI.get().registerFlag(
    new BooleanFlag("mymod:teleport", true, "Autorise la teleportation"));

ZoneCheckResult result = ArcadiaGuardAPI.get()
    .checkFlag(player, pos, MY_FLAG);
```
Guide complet : [docs/api-guide.md](https://github.com/Team-Arcadia/ArcadiaGuard/blob/standalone/docs/api-guide.md)

---

## Installation

1. Telecharger le `.jar` depuis l'onglet Files
2. Placer dans le dossier `mods/` (**serveur ET client**)
3. Lancer le serveur pour generer la config
4. Se donner le wand : `/give @s arcadiaguard:zone_editor`
5. Selectionner deux coins et ouvrir le GUI pour creer une zone

**Requis :** NeoForge 1.21.1 (toutes versions 21.1.x) — Java 21

---

## Liens

- [Code source](https://github.com/Team-Arcadia/ArcadiaGuard)
- [Guide API](https://github.com/Team-Arcadia/ArcadiaGuard/blob/standalone/docs/api-guide.md)
- [Signaler un bug](https://github.com/Team-Arcadia/ArcadiaGuard/issues)
```

---

## File Upload Settings

**File name:** `arcadiaguard-1.0.0.jar`
**Release type:** Release
**Game version:** 1.21.1
**Mod loader:** NeoForge
**Java version:** Java 21
**Environment:** Client and Server

**Changelog:**
```
Initial standalone release — v1.0.0

- Full in-game GUI for zone management
- Wand selection tool with Edit/View modes
- 60+ configurable protection flags
- Sub-zones with flag inheritance
- Dimension-level flag defaults
- 7 mod integrations (Iron's Spells, Ars Nouveau, Simply Swords, Occultism, Supplementaries, Apotheosis, Better Archeology)
- Dynamic item blocking via command
- Async audit logging with daily rotation
- LuckPerms integration
- Public API for third-party flag registration
- Mixin-based vanilla mechanic interception (fire, fluids, vines, etc.)
- Compatible with all NeoForge 21.1.x versions
```
