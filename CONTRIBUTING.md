# Contribuer

Les règles font foi dans `CLAUDE.md`.

1. Créer une branche depuis `main` à jour : `feat/<slug>`, `fix/<slug>` ou `chore/<slug>`.
2. Grosse fonctionnalité : passer par Spec Kit (`specs/README.md`).
3. Tests d'abord, puis le code minimal.
4. Commits Conventional Commits.
5. `mvn -f backend test` et `npm --prefix frontend test -- --watch=false` passent.
6. Ouvrir une PR avec le template. Le merge sur `main` déploie.
