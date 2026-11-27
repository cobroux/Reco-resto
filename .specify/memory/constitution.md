# Constitution Reco-Resto

## Principes

### I. Spec-Driven Development pour les grosses fonctionnalités
Une grosse fonctionnalité (nouveau parcours, nouveau contrat API, plusieurs jours de travail) DOIT passer par Spec Kit : `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`, `/speckit-implement`, sur une branche et dans un dossier `specs/<NNN>-<feature>/`. Le mainteneur valide chaque étape. Les petites évolutions et correctifs se font sans spec.

### II. Tests d'abord
Les tests DOIVENT être écrits avant le code et échouer avant l'implémentation. Back : `backend/src/test/java`, même package. Front : `*.spec.ts` à côté du code.

### III. Ne jamais deviner
Valeurs métier, cas limites, erreurs, forme d'un nouvel élément et changements de comportement NE DOIVENT PAS être supposés : demander, en un seul lot, avant de coder. Toute valeur non vérifiée est signalée.

### IV. DRY
Chaque connaissance a une seule représentation. Chercher l'existant avant d'écrire, étendre en place, supprimer le code remplacé.

### V. Langue et Conventional Commits
Docs, specs, commits et PR en français ; identifiants et commentaires de code en anglais. Commits en Conventional Commits ; branches `feat/`, `fix/` ou `chore/`.

## Contraintes

- Back : Java 21, Spring Boot 4.1.1, Maven, `hr.emocio.recoresto`. Front : Angular 22. Monorepo.
- Aucun secret commité ; `.env.example` à jour.

## Workflow et qualité

- Aucun commit direct sur `main`, aucun force-push.
- Les agents peuvent commiter, pousser et ouvrir des PR à la demande du mainteneur ; un merge sur `main` (qui déploie) exige son accord explicite.
- Avant une PR : `mvn -f backend test` et `npm --prefix frontend test -- --watch=false` passent.

## Gouvernance

Cette constitution prime sur les autres pratiques ; `CLAUDE.md` doit rester cohérent avec elle. Amendements par le mainteneur, avec version sémantique et date mises à jour.

**Version** : 2.0.0 | **Ratifiée** : 2026-11-27 | **Dernier amendement** : 2026-11-27
