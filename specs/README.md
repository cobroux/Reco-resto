# Spec-Driven Development avec Spec Kit

Réservé aux grosses fonctionnalités (voir `CLAUDE.md`). Templates et constitution : `.specify/`.

| Étape | Skill | Sortie dans `specs/<NNN>-<feature>/` | Pause |
|---|---|---|---|
| 1 | `/speckit-specify <besoin>` | `spec.md`, `checklists/requirements.md` | Oui |
| 2 | `/speckit-clarify` (optionnel) | `spec.md` mis à jour | Oui |
| 3 | `/speckit-plan` | `plan.md` et contrats | Oui |
| 4 | `/speckit-tasks` | `tasks.md` | Oui |
| 5 | `/speckit-implement` | code et tests | Non |
| 6 | `/speckit-analyze` (optionnel) | rapport de cohérence | Relecture |

- Une fonctionnalité = une branche = un dossier.
- La validation du mainteneur est notée par `Status: Approved` dans `spec.md`.
- La spec et le plan ne changent pas pendant l'implémentation ; les écarts sont signalés dans la PR.
