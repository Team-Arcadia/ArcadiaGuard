# ArcadiaGuard

> Mod de protection côté serveur pour NeoForge 1.21.1 — bloque les capacités magiques et les mécaniques de mods tiers dans des zones définies par les administrateurs.

---

## Présentation

ArcadiaGuard permet aux administrateurs de définir des **zones protégées** dans lesquelles les joueurs ne peuvent pas utiliser certaines capacités issues de mods de magie ou d'enchantement. Le mod intercepte les événements côté serveur et annule les actions non autorisées, sans nécessiter aucune modification côté client.

Les contrôles de zone s'intègrent nativement au **profiler Spark** via le `ProfilerFiller` de Minecraft, ce qui permet de mesurer l'impact de chaque vérification directement dans les rapports de tick.

---

## Compatibilité

| Mod | Ce qui est bloqué |
|-----|-------------------|
| [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) | Lancement de sorts |
| [Ars Nouveau](https://www.curseforge.com/minecraft/mc-mods/ars-nouveau) | Sorts, rituels, runes |
| [Simply Swords](https://www.curseforge.com/minecraft/mc-mods/simply-swords) | Capacités spéciales des épées |
| [Occultism](https://www.curseforge.com/minecraft/mc-mods/occultism) | Invocations, interactions de magie |
| [Supplementaries](https://www.curseforge.com/minecraft/mc-mods/supplementaries) | Lancer d'objets (boules de neige, etc.) |
| [Apotheosis](https://www.curseforge.com/minecraft/mc-mods/apotheosis) | Enchantements de minage radial (Tunneling) |
| [Better Archeology](https://www.curseforge.com/minecraft/mc-mods/better-archeology) | Fouille avec Tunneling |

Chaque intégration est **indépendante** et peut être activée ou désactivée séparément dans la configuration.

Le mod supporte également les zones définies par **YAWP** (Yet Another World Protector) en tant que provider externe.

---

## Installation

1. Télécharger le `.jar` depuis les releases GitHub.
2. Placer le fichier dans le dossier `mods/` du serveur.
3. Lancer le serveur une première fois pour générer le fichier de configuration.
4. Configurer les zones et les options selon vos besoins.

> ArcadiaGuard est un mod **serveur uniquement**. Les clients n'ont pas besoin de l'installer.

---

## Commandes

Toutes les commandes nécessitent le niveau d'opérateur **2** (configurable).

### Zones protégées

```
/arcadiaguard zone add <nom> <x1> <y1> <z1> <x2> <y2> <z2>
/arcadiaguard zone remove <nom>
/arcadiaguard zone list
/arcadiaguard zone info <nom>
/arcadiaguard zone whitelist add <nom> <joueur>
/arcadiaguard zone whitelist remove <nom> <joueur>
```

### Zones d'exception

Les zones d'exception permettent d'**autoriser explicitement** certaines mécaniques dans une sous-zone à l'intérieur d'une zone protégée.

```
/arcadiaguard exception add <nom> <x1> <y1> <z1> <x2> <y2> <z2>
/arcadiaguard exception remove <nom>
/arcadiaguard exception list
/arcadiaguard exception info <nom>
/arcadiaguard exception allow <nom> <feature>
/arcadiaguard exception deny <nom> <feature>
```

Features disponibles : `ironsspellbooks`, `arsnouveau`, `simplyswords`, `occultism`, `supplementaries`, `apotheosis`, `betterarcheology`

### Rechargement

```
/arcadiaguard reload
```

Recharge les zones depuis le fichier JSON sans redémarrer le serveur.

---

## Configuration

Le fichier de configuration est généré dans `config/arcadia/ArcadiaGuard/arcadiaguard-common.toml`.

```toml
[general]
  # Active les logs dans la console
  enable_logging = true
  # Ecrit les actions bloquées dans un fichier de log dédié
  log_to_file = true
  # Niveau OP requis pour bypasser les zones (0-4)
  bypass_op_level = 2

[toggles]
  enable_ironsspellbooks = true
  enable_arsnouveau = true
  enable_simplyswords = true
  enable_occultism = true
  enable_supplementaries = true
  enable_apotheosis_enchants = true
  enable_betterarcheology = true

[messages]
  message_ironsspellbooks = "Vous ne pouvez pas lancer ce sort ici."
  message_arsnouveau = "Vous ne pouvez pas utiliser Ars Nouveau ici."
  message_simplyswords = "Vous ne pouvez pas utiliser cette capacite ici."
  message_occultism = "Vous ne pouvez pas utiliser cette magie ici."
  message_supplementaries = "Vous ne pouvez pas lancer cet objet ici."
  message_apotheosis = "Vous ne pouvez pas casser ces blocs ici."
  message_betterarcheology = "Vous ne pouvez pas utiliser Tunneling ici."
```

---

## Logs d'audit

Quand `log_to_file = true`, ArcadiaGuard écrit chaque action bloquée dans :

```
logs/arcadia/ArcadiaGuard/arcadiaguard-audit.log
```

Les logs sont **rotatés automatiquement** chaque jour. Les archives sont nommées :

```
logs/arcadia/ArcadiaGuard/arcadiaguard-audit-2025-01-15.log
```

Format d'une ligne de log :

```
[2025-01-15 20:34:12] [ArcadiaGuard] player=Blushister action=arsnouveau:glyph_fire zone=spawn pos=120 64 -45
```

---

## Données des zones

Les zones sont sauvegardées dans :

```
config/arcadia/ArcadiaGuard/arcadiaguard-zones.json
```

Le fichier est géré automatiquement par les commandes. Il peut être édité manuellement puis rechargé avec `/arcadiaguard reload`.

---

## Intégration Spark

ArcadiaGuard utilise le `ProfilerFiller` natif de Minecraft. Si [Spark](https://spark.lucko.me/) est installé sur le serveur, les vérifications de zone apparaissent sous le nœud **`arcadiaguard`** dans les rapports de profiling de tick, sans aucune configuration supplémentaire.

---

## Informations techniques

- **Environnement** : Serveur uniquement (NeoForge 1.21.1)
- **Java** : 21
- **Group ID** : `com.arcadia.arcadiaguard`
- **Mod ID** : `arcadiaguard`
- **Licence** : All Rights Reserved

---

## Auteur

**[Blushister](https://github.com/Blushister)**
