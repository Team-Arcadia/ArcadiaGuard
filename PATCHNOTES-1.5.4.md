# 1.5.4 — Correctif warning spam

Patch correctif sur la 1.5.3. Le fix de spawn du patch précédent laissait les entités annulées dans un état intermédiaire, provoquant un spam de warnings `"Tried to add entity X but it was marked as removed already"` dans la console serveur.

---

## 🐛 Corrections

### Spam de warnings `marked as removed` dans la console
Quand `mob-spawn-list` bloquait une entité via `EntityJoinLevelEvent` (fix 1.5.3), l'entité était correctement empêchée d'entrer dans le monde, mais n'était pas discardée. Le serveur ou les mods tiers (ex. Hominid) pouvaient alors tenter de la réutiliser et logguer un warning à chaque tentative.

L'entité est maintenant explicitement discardée lors du blocage.

---

## ℹ️ Note comportementale

Le flag `mob-spawn-list` bloque toutes les méthodes de spawn dans la zone : spawn naturel, `/summon`, œufs de spawn. C'est le comportement attendu pour un mod de protection. Pour tester le spawn d'un mob bloqué, sortez de la zone ou retirez temporairement le mob de la liste.

---

## 📦 Compatibilité

- **NeoForge 1.21.1** (toutes versions 21.1.x)
- **Java 21**
- Client + Serveur (les deux doivent être à jour)
- **Aucun changement de format de données** : upgrade direct depuis 1.5.3

---
---

# 1.5.4 — Warning spam fix

Bugfix patch on top of 1.5.3. The spawn fix from the previous patch left cancelled entities in an intermediate state, causing `"Tried to add entity X but it was marked as removed already"` warning spam in the server console.

---

## 🐛 Fixes

### `marked as removed` warning spam in console
When `mob-spawn-list` blocked an entity via `EntityJoinLevelEvent` (1.5.3 fix), the entity was correctly prevented from entering the world but was not discarded. The server or third-party mods (e.g. Hominid) could then attempt to reuse it and log a warning on each attempt.

The entity is now explicitly discarded when blocked.

---

## ℹ️ Behaviour note

The `mob-spawn-list` flag blocks all spawn methods within the zone: natural spawning, `/summon`, spawn eggs. This is the intended behaviour for a protection mod. To test spawning a blocked mob, either move outside the zone or temporarily remove the mob from the list.

---

## 📦 Compatibility

- **NeoForge 1.21.1** (all 21.1.x versions)
- **Java 21**
- Client + Server (both must be updated)
- **No data format change**: direct upgrade from 1.5.3
