# ArcadiaGuard

> Mod de protection standalone pour NeoForge 1.21.1 — systeme de zones configurable avec GUI in-game, wand de selection, flags granulaires et rendu 3D des limites de zone.

---

## Presentation

ArcadiaGuard est un mod de protection complet permettant aux administrateurs de definir des **zones protegees** directement en jeu. Tout se gere depuis l'**interface graphique** : creation de zones, configuration des flags, gestion des membres, consultation des logs. Aucune commande n'est necessaire pour l'utilisation courante.

Le mod dispose d'un systeme de **~60 flags** configurables par zone et par dimension, avec heritage parent/enfant et resolution automatique des conflits.

---

## Interface graphique

Le GUI est le coeur de la gestion d'ArcadiaGuard. Il s'ouvre avec le **wand** (clic droit sans cible) ou via `/arcadiaguard gui`.

### Liste des zones

Vue tabulaire de toutes les zones avec :
- **Filtres par dimension** dans la sidebar (Overworld, Nether, End, dimensions moddees)
- **Filtres par type** (toutes, racines, sous-zones)
- **Recherche** par nom ou dimension
- **Pagination** pour les serveurs avec beaucoup de zones
- **Preview** de la zone selectionnee (stats, coordonnees, flags)

### Detail de zone

Ecran complet pour gerer une zone :
- **Activer/desactiver** la zone en un clic
- **Flags** : ajouter, modifier, supprimer des flags avec le flag picker (recherche integree). Les flags booleens se togglent directement, les flags int/liste ouvrent un editeur dedie.
- **Membres** : ajouter/retirer des joueurs de la whitelist
- **Coordonnees** : visualiser et editer manuellement les coins A/B, ou redefiner via le wand
- **Hierarchie** : definir un parent pour l'heritage de flags
- **Logs** : consulter l'historique des actions bloquees avec filtres joueur/action
- **Voir la zone** : activer le rendu 3D des limites en jeu

### Flags de dimension

Chaque dimension peut avoir ses propres flags par defaut, independamment des zones. Accessible via l'icone engrenage a cote de chaque dimension dans la sidebar.

### Creation de zone

1. Selectionner les coins A et B avec le wand (clic gauche / clic droit)
2. Ouvrir le GUI
3. Cliquer "Creer zone" — le formulaire affiche les coordonnees, la surface, le volume et le nombre de chunks en temps reel

---

## Wand de selection

L'item `zone_editor` a deux modes :

| Mode | Usage |
|------|-------|
| **EDIT** | Clic gauche = coin A, clic droit = coin B. Positions affichees dans le HUD. |
| **VIEW** | Visualisation passive des limites de zones en wireframe 3D colore. |

Le HUD affiche les positions selectionnees au-dessus de la barre d'XP.

---

## Protection des mods

Chaque integration est independante et configurable :

| Mod | Ce qui est bloque |
|-----|-------------------|
| [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) | Lancement de sorts |
| [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) | Sorts, teleportation warp scroll, portails |
| [Simply Swords](https://www.curseforge.com/minecraft/mc-mods/simply-swords) | Capacites speciales des armes |
| [Occultism](https://www.curseforge.com/minecraft/mc-mods/occultism) | Rituels et magie |
| [Supplementaries](https://www.curseforge.com/minecraft/mc-mods/supplementaries) | Lancer d'objets |
| [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) | Tunneling, affix Enlightened |
| [Better Archeology](https://www.curseforge.com/minecraft/mc-mods/better-archeology) | Enchantement Tunneling |

Tous les mods sont **optionnels** — ArcadiaGuard fonctionne quel que soit l'environnement.

---

## Flags

Le mod inclut ~60 flags couvrant :

- Interactions blocs (casse, pose, interactions)
- PvP et degats d'entites
- Spawn de mobs et invocations
- Sorts et capacites magiques (par mod)
- Utilisation d'items specifiques (leads, spawn eggs, livres)
- Propagation de fluides, feu, explosions
- Blocage dynamique d'items (ajout/retrait en temps reel sans redemarrage)

Les flags supportent 3 types : **booleen** (allow/deny), **entier** (limites), **liste** (blacklists/whitelists).

Des flags custom peuvent etre ajoutes par des mods tiers via l'API — voir le [guide d'integration API](docs/api-guide.md).

---

## Installation

1. Telecharger le `.jar` depuis les releases GitHub.
2. Placer le fichier dans le dossier `mods/` (**serveur ET client**).
3. Lancer le serveur une premiere fois pour generer la configuration.
4. Se donner le wand : `/give @s arcadiaguard:zone_editor`
5. Selectionner deux coins et ouvrir le GUI pour creer une zone.

> Le client fournit le GUI de gestion et le rendu des zones. Le serveur gere la protection.

---

## Commandes

> La plupart des actions sont realisables directement depuis le GUI. Les commandes restent disponibles pour l'automatisation ou l'utilisation console.

```
/arcadiaguard gui                                    — ouvrir le GUI
/arcadiaguard zone add <nom> <x1> <y1> <z1> <x2> <y2> <z2>
/arcadiaguard zone remove <nom>
/arcadiaguard zone list
/arcadiaguard zone whitelist add <nom> <joueur>
/arcadiaguard flag set <zone> <flag> <valeur>
/arcadiaguard flag list
/arcadiaguard item block <item>                      — bloquer un item dynamiquement
/arcadiaguard item unblock <item>
/arcadiaguard reload
/arcadiaguard debug
/arcadiaguard log <zone> [joueur] [action]
/arcadiaguard migrate                                — migrer depuis un ancien format
```

Niveau OP requis : **2** (configurable).

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

## API pour mods tiers

ArcadiaGuard expose une API publique permettant aux mods tiers d'enregistrer leurs propres flags de protection et de verifier les permissions dans les zones.

```java
// Enregistrer un flag custom
ArcadiaGuardAPI.get().registerFlag(
    new BooleanFlag("mymod:teleport", true, "Autorise la teleportation"));

// Verifier un flag
ZoneCheckResult result = ArcadiaGuardAPI.get().checkFlag(player, pos, MY_FLAG);
if (result.blocked()) { /* action bloquee */ }
```

Guide complet : **[docs/api-guide.md](docs/api-guide.md)**

---

## Extensibilite datapack

Les listes d'items sont pilotees par des tags overridables sans recompilation :

- `arcadiaguard:banned_leads`
- `arcadiaguard:banned_spawn_eggs`
- `arcadiaguard:spawn_banned_books`

---

## Informations techniques

- **Environnement** : Client + Serveur (NeoForge 1.21.1)
- **Java** : 21
- **Mod ID** : `arcadiaguard`
- **Licence** : [MIT](LICENSE)

---

## Auteur

**[Blushister](https://github.com/Blushister)**
