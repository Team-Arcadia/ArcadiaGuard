# ArcadiaGuard 1.5.0 — Première publication

> **NeoForge 1.21.1 · Java 21 · Client + Serveur**

Bienvenue sur ArcadiaGuard, un mod de protection de zones pensé pour les serveurs modés. Cette page est la première version publiée sur CurseForge : le mod est **prêt pour la production**, testé sur un modpack de 477 mods avec 1 000+ zones sans la moindre régression.

---

## 🎯 Ce que fait ArcadiaGuard

Vous tracez des zones dans le monde avec une baguette, puis vous configurez tout depuis une **interface graphique in-game** : blocs/PvP/spawn/magie/parkour/items/commandes… tout est toggleable par zone et par dimension, avec héritage parent/enfant.

Aucune commande n'est nécessaire pour utiliser le mod au quotidien. Tout passe par le GUI.

---

## ✨ Fonctionnalités phares

### Interface graphique complète
- **Panel central** listant toutes les zones, avec sidebar de filtres par dimension, recherche (F / Ctrl+F), preview de zone
- **Scrollbar fluide** — pas de pagination, affichez-en 10 000 si vous voulez
- **Fiche de zone** : flags, membres, coordonnées, parent, logs d'audit, activation/désactivation
- **Éditeur de dim flags** : configurez des règles par défaut pour chaque dimension
- **Flag picker** avec recherche intégrée et types bool/int/list
- **Badge INH** sur chaque flag hérité pour savoir d'où vient la valeur
- **Rendu 3D des zones** en jeu via le wand en mode VIEW

### 86 flags configurables
Couvrant toutes les catégories classiques :
- **Blocs** : casse, pose, interactions, conteneurs, portes/trappes/leviers/boutons/plaques
- **Combat** : PvP, dégâts joueur/mob/chute, invincibilité, attaque animaux/monstres
- **Spawn** : mobs, animaux, monstres, villageois, mutants
- **Items** : drop, pickup, throw, leashes, œufs de spawn, seaux à mob
- **Explosions & propagation** : creeper/TNT/block, feu, lave, eau, lierre, neige/glace
- **Mouvement** : entrée/sortie, fly, élytre, portails
- **Chat & commandes** : chat, commandes vanilla, blacklist par liste
- **Gameplay** : pousses, piétinement, labourage, écorçage, chemins, fluides
- **Performance** : chunkload (force le chargement des chunks de la zone)

Et 15+ flags pour les mods compat (voir ci-dessous).

### Intégration LuckPerms
Si [LuckPerms](https://www.curseforge.com/minecraft/mc-mods/luckperms) est installé, ArcadiaGuard y délègue les permissions, **avec refresh à chaud (TTL 5 s)** : retirez ou donnez un bypass, l'effet est immédiat, sans reconnexion du joueur.

| Node | Effet |
|------|-------|
| `arcadiaguard.*` | Bypass total |
| `arcadiaguard.zone.bypass` | Bypass sans accès admin |
| `arcadiaguard.view` | **Lecture seule du panel** (modération sans édition) |
| `arcadiaguard.zone.<nom>.{owner\|moderator\|member}` | Rôles par zone |

Sans LuckPerms, le niveau OP vanilla fait le travail (configurable, défaut **OP 2**).

### Mods compat (tous optionnels, détectés à chaud)
Ars Nouveau · Iron's Spells 'n Spellbooks · Simply Swords · Occultism · Supplementaries · Apotheosis · Better Archeology · Carry On · ParCool · Waystones · Emotecraft · Sophisticated Storage · Twilight Forest · Mutant Monsters · Rechiseled

Aucun hard-dep : installez seulement ce qui vous intéresse, ArcadiaGuard s'adapte.

### Sécurité & fiabilité
- **Bypass OP via `getProfilePermissions()`** plutôt que l'API NeoForge, pour bloquer un vecteur où d'autres mods pouvaient accorder un bypass involontaire
- **Écriture asynchrone coalescée** : un burst de N modifications sur la même zone = au plus une écriture disque
- **Logs d'audit** avec throttle anti-spam et rotation journalière
- **API publique** pour que les mods tiers enregistrent leurs propres flags et vérifient les permissions

---

## 🔧 Wand de sélection

L'item `arcadiaguard:zone_editor` a deux modes :

| Mode | Usage |
|------|-------|
| **EDIT** | Clic gauche = coin A, clic droit = coin B |
| **VIEW** | Visualisation passive des limites de zones en wireframe 3D |

Positions affichées dans le HUD au-dessus de la barre d'XP.

---

## 📦 Installation

1. Téléchargez `arcadiaguard-1.5.0.jar`
2. Placez-le dans `mods/` (**client ET serveur**)
3. Démarrez le serveur une fois pour générer la config
4. En jeu : `/give @s arcadiaguard:zone_editor` pour obtenir le wand
5. Sélectionnez deux coins, ouvrez le GUI (clic droit sans cible), créez votre première zone

**Requis** : NeoForge 1.21.1 · Java 21 · Minecraft 1.21.1

---

## ⚙ Commandes

La plupart des actions se font dans le GUI. Les commandes restent disponibles pour la console :

```
/ag gui                            ouvrir le GUI
/ag wand give                      donner le wand
/ag zone add <nom> <x1 y1 z1> <x2 y2 z2>
/ag zone remove <nom>
/ag zone list
/ag zone whitelist add|remove <nom> <joueur>
/ag zone parent set|clear <enfant> [<parent>]
/ag flag set <zone> <flag> <valeur>
/ag flag reset <zone> <flag>
/ag dimflag set|reset <dim> <flag> [<valeur>]
/ag item block|unblock <item>
/ag log <zone> [<joueur>] [<action>]
/ag debug                          bascule mode debug (désactive bypass)
/ag reload
/ag migrate                        migrer depuis un format legacy
```

`/arcadiaguard` est l'alias complet de `/ag`. Niveau OP requis : **2**.

---

## 📁 Fichiers générés

- `config/arcadia/ArcadiaGuard/arcadiaguard-common.toml` — config principale
- `config/arcadia/ArcadiaGuard/zones/<dim>/<nom>.json` — une zone = un fichier
- `config/arcadia/ArcadiaGuard/dimension-flags.json` — flags par dimension
- `config/arcadia/ArcadiaGuard/blocked-items.json` — items bloqués dynamiquement
- `logs/arcadia/ArcadiaGuard/arcadiaguard-audit.log` — audit (rotation journalière)

---

## 🧑‍💻 Pour les dev

L'API permet à tout mod tiers d'enregistrer ses flags et d'appeler les checks ArcadiaGuard :

```java
// Enregistrer un flag custom
ArcadiaGuardAPI.get().registerFlag(
    new BooleanFlag("mymod:teleport", true, "Autorise la téléportation"));

// Vérifier un flag à une position
ZoneCheckResult result = ArcadiaGuardAPI.get().checkFlag(player, pos, MY_FLAG);
if (result.blocked()) { /* action refusée */ }
```

Guide complet : `docs/api-guide.md` dans le repo GitHub.

---

## 🙏 Remerciements

Merci aux testeurs du serveur Arcadia pour les 500+ retours pendant les 6 phases de QA, et aux auteurs des mods compat pour leurs APIs publiques propres.

**Auteur** : [Blushister](https://github.com/Blushister)
**Licence** : MIT
**Repo** : [github.com/Team-Arcadia/ArcadiaGuard](https://github.com/Team-Arcadia/ArcadiaGuard)
