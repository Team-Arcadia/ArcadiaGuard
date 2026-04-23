# ArcadiaGuard

> Mod de protection de zones pour NeoForge 1.21.1 — 86 flags configurables, interface graphique complete, integration LuckPerms, rendu 3D des zones en jeu.

---

## Presentation

ArcadiaGuard permet aux administrateurs de definir des **zones protegees** directement en jeu. Tout se gere depuis l'**interface graphique** (panel central, fiche de zone, editeur de flags) : creation, configuration des flags, gestion des membres, consultation des logs, flags de dimension. Les commandes restent disponibles pour la console et l'automatisation, mais aucune n'est necessaire pour l'utilisation courante.

Le mod dispose de **86 flags** (79 booleens, 2 entiers, 5 listes) organises par frequence, avec heritage parent/enfant, flags de dimension, et resolution automatique des conflits.

---

## Interface graphique

Le GUI est le coeur de la gestion d'ArcadiaGuard. Il s'ouvre avec le **wand** (clic droit sans cible) ou via `/ag gui`.

### Liste des zones

Vue tabulaire de toutes les zones avec :
- **Filtres par dimension** dans la sidebar (Overworld, Nether, End, dimensions moddees chargees)
- **Filtres par type** (toutes, racines, sous-zones)
- **Recherche** par nom ou dimension (`F` / `Ctrl+F`)
- **Scroll** fluide — pas de pagination, toutes les zones du serveur affichees (cap defensif a 10 000 zones)
- **Preview** de la zone selectionnee (stats, coordonnees, flags)
- **Mode lecture seule** pour les moderateurs via la permission `arcadiaguard.view` (boutons d'edition grises)

### Detail de zone

Ecran complet pour gerer une zone :
- **Activer/desactiver** la zone en un clic
- **Flags** : ajouter, modifier, retirer des flags avec le flag picker (recherche integree). Les booleens se togglent directement, les entiers/listes ouvrent un editeur dedie.
- **Badge `INH`** sur chaque flag herite (parent / dimension / defaut) pour savoir d'ou vient la valeur
- **Membres** : ajouter/retirer des joueurs de la whitelist
- **Coordonnees** : visualiser ou redefinir les coins A/B via le wand
- **Hierarchie** : definir un parent pour l'heritage de flags (bypass du parent → bypass de l'enfant)
- **Logs** : historique des actions bloquees avec filtres joueur/action
- **Voir la zone** : activer le rendu 3D des limites en jeu

### Flags de dimension

Chaque dimension peut avoir ses propres flags par defaut, independamment des zones. Accessibles via l'icone engrenage a cote de chaque dimension dans la sidebar, ou via le bouton `Flags dim` du footer.

### Creation de zone

1. Selectionner les coins A et B avec le wand (clic gauche / clic droit)
2. Ouvrir le GUI
3. Cliquer **Creer zone** — le formulaire affiche coordonnees, surface, volume, et chunks affectes en temps reel

---

## Wand de selection

L'item `arcadiaguard:zone_editor` a deux modes (switch via molette + touche) :

| Mode | Usage |
|------|-------|
| **EDIT** | Clic gauche = coin A, clic droit = coin B. Positions affichees dans le HUD au-dessus de la barre d'XP. |
| **VIEW** | Visualisation passive des limites de zones en wireframe 3D colore par dimension. |

---

## Integration LuckPerms

Si [LuckPerms](https://www.curseforge.com/minecraft/mc-mods/luckperms) est installe, ArcadiaGuard lui delegue la gestion des permissions. Le cache d'etat est invalide avec un TTL de 5 s pour refleter les changements a chaud sans reconnexion.

| Node | Effet |
|------|-------|
| `arcadiaguard.*` | Bypass total (equivalent OP) |
| `arcadiaguard.zone.bypass` | Bypass sans acces admin |
| `arcadiaguard.view` | Lecture seule du panel (moderation sans edition) |
| `arcadiaguard.zone.<nom>.owner` | Proprietaire d'une zone (gestion complete) |
| `arcadiaguard.zone.<nom>.moderator` | Moderateur (flags + membres + voir logs) |
| `arcadiaguard.zone.<nom>.member` | Acces de base (bypass dans cette zone) |

Sans LuckPerms, le mod utilise le niveau OP vanilla (configurable, defaut `2`) comme critere de bypass.

---

## Protection des mods

Chaque integration est independante et n'est chargee que si le mod tiers est present :

| Mod | Ce qui est bloque |
|-----|-------------------|
| [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) | Lancement de sorts |
| [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) | Sorts, warp scroll, portails, additions scrolls |
| [Simply Swords](https://www.curseforge.com/minecraft/mc-mods/simply-swords) | Capacites speciales des armes |
| [Occultism](https://www.curseforge.com/minecraft/mc-mods/occultism) | Rituels et magie |
| [Supplementaries](https://www.curseforge.com/minecraft/mc-mods/supplementaries) | Lancer d'objets |
| [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) | Tunneling, charms, affix Enlightened |
| [Better Archeology](https://www.curseforge.com/minecraft/mc-mods/better-archeology) | Enchantement Tunneling |
| [Carry On](https://www.curseforge.com/minecraft/mc-mods/carry-on) | Ramassage de blocs/entites |
| [ParCool](https://www.curseforge.com/minecraft/mc-mods/parcool) | Actions de parkour |
| [Waystones](https://www.curseforge.com/minecraft/mc-mods/waystones) | Utilisation et teleportation |
| [Emotecraft](https://www.curseforge.com/minecraft/mc-mods/emotecraft) | Emotes dans les zones |
| [Sophisticated Storage](https://www.curseforge.com/minecraft/mc-mods/sophisticated-storage) | Coffres custom + minecarts a coffre |
| [Twilight Forest](https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest) | Projectiles speciaux |
| [Mutant Monsters](https://www.curseforge.com/minecraft/mc-mods/mutant-monsters) | Spawn de mutants |
| [Rechiseled](https://www.curseforge.com/minecraft/mc-mods/rechiseled) | Utilisation des blocs Rechiseled |

---

## Flags

86 flags repartis par categorie (bouton `Frequence` dans le flag picker) :

- **Blocs** : casse, pose, interactions, conteneurs, portes/trappes/boutons/leviers/plaques
- **PvP & degats** : pvp, player-damage, mob-damage, fall-damage, invincibilite, attaque animaux/monstres
- **Spawn** : mob-spawn, animal-spawn, monster-spawn, villager-spawn, mutant-mob-spawn
- **Items** : item-drop, item-pickup, item-throw, leash, spawn-egg, mob-bucket, exp-drop
- **Explosions & propagations** : creeper/tnt/block, fire-spread, lava-fire/spread, water-spread, leaf-decay, grass/vine/sculk
- **Mouvement** : entry, exit, fly, use-elytra, use-portal
- **Chat & commandes** : send-chat, exec-command (+ blacklist par liste), npc-interact
- **Economies de temps** : crop-growth, tree-growth, farmland-trample, snow/ice-melt, till-farmland, strip-wood, shovel-path, scoop/place-fluids
- **Mods compat** : ars/irons/simplyswords/occultism/supplementaries/parcool/emote/charm/waystone/rechiseled…
- **Chunkload** : force les chunks de la zone a rester charges

Types supportes : **booleen** (allow/deny), **entier** (seuils comme heal-amount), **liste** (blacklists/whitelists).

Les flags peuvent etre enrichis par des mods tiers via l'API — voir [docs/api-guide.md](docs/api-guide.md).

---

## Installation

1. Telecharger le `.jar` depuis les releases GitHub.
2. Placer le fichier dans `mods/` (**serveur ET client** — client pour le GUI, serveur pour la protection).
3. Lancer le serveur une fois pour generer la configuration.
4. Obtenir le wand : `/give @s arcadiaguard:zone_editor`
5. Selectionner deux coins et ouvrir le GUI pour creer la premiere zone.

> Le mod fonctionne sans aucun mod tiers. Toutes les integrations sont optionnelles.

---

## Commandes

La majorite des actions passent par le GUI. Les commandes sont principalement utiles pour la console et l'automatisation.

```
/ag gui                                   ouvrir le GUI
/ag wand give                             donner le wand de selection
/ag zone add <nom> <x1> <y1> <z1> <x2> <y2> <z2>
/ag zone remove <nom>
/ag zone list
/ag zone whitelist add|remove <nom> <joueur>
/ag zone parent set|clear <enfant> [<parent>]
/ag flag set <zone> <flag> <valeur>
/ag flag reset <zone> <flag>
/ag flag list
/ag dimflag set|reset <dim> <flag> [<valeur>]
/ag item block <item>                     bloquer un item dynamiquement
/ag item unblock <item>
/ag log <zone> [<joueur>] [<action>]
/ag debug                                 toggle mode debug (bypass desactive)
/ag reload                                recharger config / zones / flags dim
/ag migrate                               migrer depuis un format legacy
```

`/arcadiaguard` est l'alias complet de `/ag`. Niveau OP requis : **2** (configurable via `bypass_op_level`).

---

## Configuration

Fichier principal : `config/arcadia/ArcadiaGuard/arcadiaguard-common.toml`

```toml
[general]
  enable_logging              = true
  bypass_op_level             = 2
  async_writer_policy         = "BLOCK"       # BLOCK | FAIL_FAST
  disabled_flag_frequencies   = []            # e.g. ["VERBOSE"]

[messages]
  # Messages personnalisables affiches au joueur lors d'un blocage
  block_break = "Vous ne pouvez pas casser des blocs ici."
  # ... (un message par flag booleen)
```

---

## Donnees

| Chemin | Contenu |
|--------|---------|
| `config/arcadia/ArcadiaGuard/zones/<dim>/<nom>.json` | Une zone = un fichier JSON, organise par dimension |
| `config/arcadia/ArcadiaGuard/dimension-flags.json` | Flags par defaut par dimension |
| `config/arcadia/ArcadiaGuard/blocked-items.json` | Items bloques dynamiquement |
| `logs/arcadia/ArcadiaGuard/arcadiaguard-audit.log` | Logs d'audit (rotation journaliere, coalescence anti-spam) |

Les ecritures de zones sont **coalescees et asynchrones** via `AsyncZoneWriter` — un burst de N modifications sur la meme zone produit au plus une ecriture disque.

---

## API pour mods tiers

```java
// Enregistrer un flag custom (boolean / int / list)
ArcadiaGuardAPI.get().registerFlag(
    new BooleanFlag("mymod:teleport", true, "Autorise la teleportation"));

// Verifier un flag a une position
ZoneCheckResult result = ArcadiaGuardAPI.get().checkFlag(player, pos, MY_FLAG);
if (result.blocked()) { /* action refusee — result.zoneName() identifie la zone */ }
```

Guide complet : **[docs/api-guide.md](docs/api-guide.md)**.

---

## Extensibilite datapack

Les listes d'items sont pilotees par des tags overridables sans recompilation :

- `arcadiaguard:banned_leads`
- `arcadiaguard:banned_spawn_eggs`
- `arcadiaguard:spawn_banned_books`

---

## Informations techniques

- **Environnement** : Client + Serveur (NeoForge 1.21.1, toutes versions 21.1.x)
- **Java** : 21
- **Mod ID** : `arcadiaguard`
- **Version** : 1.5.0
- **Licence** : [MIT](LICENSE)

---

## Auteur

**[Blushister](https://github.com/Blushister)**
