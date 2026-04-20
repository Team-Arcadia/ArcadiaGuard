# ArcadiaGuard

> Mod de protection standalone pour NeoForge 1.21.1 — systeme de zones configurable avec GUI in-game, wand de selection, flags granulaires et rendu 3D des limites de zone.

---

## Presentation

ArcadiaGuard est un mod de protection complet permettant aux administrateurs de definir des **zones protegees** avec un systeme de flags granulaire (~60 flags). Le mod dispose de son propre systeme de zones interne, d'une **interface graphique** accessible en jeu, et d'un **wand de selection** pour definir les zones visuellement.

Chaque zone peut etre configuree individuellement via des flags booleens, entiers ou listes. Les flags supportent l'heritage parent/enfant et les overrides par dimension.

---

## Fonctionnalites principales

### Systeme de zones

- Zones definies en jeu avec le **wand de selection** (clic droit/gauche = coin A/B)
- Sous-zones avec heritage de flags du parent
- Whitelist de joueurs par zone (membre, moderateur, proprietaire)
- Flags configurables par zone ET par dimension
- Resolution automatique des conflits de zones (priorite > volume > nom)

### Interface graphique (GUI)

- **Liste des zones** — vue tabulaire avec filtres par dimension, recherche, pagination
- **Detail de zone** — stats, flags, membres, coordonnees editables
- **Flag picker** — ajout/suppression de flags avec recherche
- **Editeur de dimension** — flags au niveau dimension
- **Logs d'audit** — historique filtre des actions bloquees
- **Creation de zone** — formulaire avec preview en temps reel

### Wand de selection

- Item `zone_editor` avec deux modes : **EDIT** (selection) et **VIEW** (visualisation passive)
- Affichage HUD des positions selectionnees (au-dessus de la barre XP)
- Rendu 3D des limites de zones en wireframe colore

### Protection des mods

Chaque integration est independante et peut etre activee/desactivee dans la config :

| Mod | Ce qui est bloque |
|-----|-------------------|
| [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) | Lancement de sorts |
| [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) | Sorts, teleportation warp scroll, portails |
| [Simply Swords](https://www.curseforge.com/minecraft/mc-mods/simply-swords) | Capacites speciales des armes |
| [Occultism](https://www.curseforge.com/minecraft/mc-mods/occultism) | Rituels et magie |
| [Supplementaries](https://www.curseforge.com/minecraft/mc-mods/supplementaries) | Lancer d'objets |
| [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) | Tunneling, affix Enlightened |
| [Better Archeology](https://www.curseforge.com/minecraft/mc-mods/better-archeology) | Enchantement Tunneling |

### Protection d'items

- Laisses vanilla et ender laisses (Apothic Enchanting)
- Oeufs de spawn (verification bloc + face de spawn)
- Livres vanilla (anti-duplication)
- **Blocage dynamique d'items** via commande, sans redemarrage

### Flags

Le mod inclut ~60 flags couvrant :

- Interactions blocs (casse, pose, interactions)
- PvP et degats d'entites
- Spawn de mobs et invocations
- Sorts et capacites magiques (par mod)
- Utilisation d'items specifiques
- Propagation de fluides, feu, explosions
- Et plus encore via l'API publique

---

## Installation

1. Telecharger le `.jar` depuis les releases GitHub.
2. Placer le fichier dans le dossier `mods/` (serveur ET client).
3. Lancer le serveur une premiere fois pour generer la configuration.
4. Utiliser le wand in-game ou les commandes pour creer des zones.

> ArcadiaGuard necessite une installation **client et serveur**. Le client fournit le GUI de gestion et le rendu des zones.

---

## Commandes

Toutes les commandes sont sous `/arcadiaguard` (niveau OP 2 par defaut).

### Zones

```
/arcadiaguard zone add <nom> <x1> <y1> <z1> <x2> <y2> <z2>
/arcadiaguard zone remove <nom>
/arcadiaguard zone list
/arcadiaguard zone whitelist add <nom> <joueur>
/arcadiaguard zone whitelist remove <nom> <joueur>
```

### Flags

```
/arcadiaguard flag set <zone> <flag> <valeur>
/arcadiaguard flag get <zone> <flag>
/arcadiaguard flag list
```

### Items dynamiques

```
/arcadiaguard item block <item>
/arcadiaguard item unblock <item>
/arcadiaguard item list
```

### Utilitaires

```
/arcadiaguard reload
/arcadiaguard debug
/arcadiaguard log <zone> [joueur] [action]
/arcadiaguard migrate
```

---

## Configuration

Fichier : `config/arcadia/ArcadiaGuard/arcadiaguard-common.toml`

```toml
[general]
  enable_logging = true
  log_to_file = true
  bypass_op_level = 2

[toggles]
  enable_ironsspellbooks = true
  enable_arsnouveau = true
  enable_simplyswords = true
  enable_occultism = true
  enable_supplementaries = true
  enable_apotheosis_enchants = true
  enable_betterarcheology = true
```

---

## Donnees

| Fichier | Contenu |
|---------|---------|
| `config/arcadia/ArcadiaGuard/arcadiaguard-zones.json` | Zones et flags |
| `config/arcadia/ArcadiaGuard/arcadiaguard-dim-flags.json` | Flags par dimension |
| `config/arcadia/ArcadiaGuard/arcadiaguard-blocked-items.json` | Items bloques dynamiquement |
| `logs/arcadia/ArcadiaGuard/arcadiaguard-audit.log` | Logs d'audit (rotation journaliere) |

---

## API publique

ArcadiaGuard expose une API pour les mods tiers via `ArcadiaGuardAPI` :

```java
// Enregistrer un flag custom
ArcadiaGuardAPI.get().flagRegistry().register(myFlag);

// Verifier un flag dans une zone
ArcadiaGuardAPI.get().guardService().isFlagDenied(level, pos, MyFlags.MY_FLAG);
```

---

## Extensibilite datapack

Les listes d'items sont pilotees par des tags overridables :

- `arcadiaguard:banned_leads`
- `arcadiaguard:banned_spawn_eggs`
- `arcadiaguard:spawn_banned_books`

---

## Informations techniques

- **Environnement** : Client + Serveur (NeoForge 1.21.1)
- **Java** : 21
- **Mod ID** : `arcadiaguard`
- **Licence** : All Rights Reserved

---

## Auteur

**[Blushister](https://github.com/Blushister)**
