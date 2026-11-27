# Reco-Resto

Application web mobile de recommandation de restaurants : recherche en langage naturel, deck à swiper, fiche enrichie, carte avec trajet à pied. Un seul développeur pour le moment.

## Stack et arborescence

- `backend/` : Java 21, Spring Boot 4.1.1, Maven, package `hr.emocio.recoresto` (Google Places, LLM). Pas de base de données.
- `frontend/` : Angular 22, Vitest, MapLibre.
- `specs/<NNN>-<feature>/` : documents Spec Kit. `.specify/` : templates et constitution.
- `.claude/agents/` : l'équipe. `.claude/skills/` : Spec Kit (`/speckit-*`).

## Commandes

- Backend : `mvn -f backend test`, `mvn -f backend spring-boot:run`
- Frontend : `npm --prefix frontend install|start`, `npm --prefix frontend test -- --watch=false`, `npm --prefix frontend run build`

## Se repérer avant de chercher

- Code : `graphify update .` (gratuit, sans LLM) après des modifications, puis `graphify explain "Symbole"`, `graphify path "A" "B"` ou `graphify query "NomDeSymbole" --budget 1500`. Un nom de symbole marche mieux qu'une phrase ; en cas d'ambiguïté, relancer avec l'id complet que la commande propose. Le graphe ne couvre que le code (`graphify-out/`, ignoré par git) : ni specs, ni docs, ni YAML.
- Historique et décisions passées : claude-mem (`mem_search`).
- Puis lire seulement les fichiers désignés. Le code fait foi sur le graphe et la mémoire.

## Langue

- En français : échanges, docs, specs, agents, messages de commit, PR.
- En anglais : identifiants et commentaires de code (cohérence avec l'existant).
- Les documents existants en anglais ne sont traduits que s'ils sont réécrits.

## Git et déploiement

- Branches `feat/<slug>`, `fix/<slug>`, `chore/<slug>`. Jamais de commit ni de push direct sur `main`, jamais de force-push.
- Commits : une ligne Conventional Commits.
- Les agents peuvent commiter, pousser et ouvrir des PR quand je le demande.
- **Un merge sur `main` déploie en production** (`.github/workflows/deploy.yml`, voir `docs/deployment.md`) : ne jamais merger sans mon accord explicite.
- Aucun secret dans le dépôt ; tenir `.env.example` à jour.

## Règles de travail

- **Ne jamais deviner.** Valeurs métier, cas limites, erreurs, emplacement ou forme d'un nouvel élément, changement de comportement : demander, toutes les questions en une fois, avant de coder. Signaler toute valeur non vérifiée. Les conventions établies s'appliquent sans demander.
- **DRY.** Chercher l'existant (par comportement et mots-clés) avant d'écrire ; étendre en place, mettre à jour appelants et tests, supprimer le code remplacé. Dire ce qui a été réutilisé.
- **Minimal (ponytail).** Le plus petit changement complet ; pas d'abstraction ni d'option non demandée ; pas de dépendance pour quelques lignes. Ne jamais rogner validation aux frontières, sécurité, accessibilité.
- **Tests d'abord**, vus échouer. Back : `backend/src/test/java` (même package) ; front : `*.spec.ts` à côté du code.
- **Bug :** trouver la cause racine avant de corriger (`superpowers:systematic-debugging`).
- **Terminé = vérifié** : commande lancée et sortie lue avant d'annoncer que ça marche.
- **Réponses courtes**, sans récapitulatif.

## Spec Kit

Réservé aux grosses fonctionnalités (nouveau parcours, nouveau contrat API, plusieurs jours) : `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`, avec ma validation à chaque pause (voir `specs/README.md`). Petites évolutions et correctifs : en direct, avec tests.

## Équipe

La session principale fait elle-même les petites tâches. Elle délègue seulement les tâches lourdes ou parallélisables, ou quand je le demande.

| Agent | Quand | Modèle / effort |
|---|---|---|
| `product-owner` | besoin flou, périmètre, critères d'acceptation | sonnet / medium |
| `architect` | contrat API, découpage, décision structurante | opus / medium |
| `ux-designer` | parcours, écran, motion, accessibilité | opus / medium |
| `frontend-dev` | code Angular | sonnet / medium |
| `backend-dev` | code Spring Boot | sonnet / medium |
| `devops` | Docker, CI/CD, EC2, Caddy, nginx | sonnet / medium |
| `reviewer` | avant PR : bugs, sécurité, sur-ingénierie | opus / medium |
| `qa` | lancer tests et application, vérifier un parcours | haiku / medium |

Jamais d'Opus en effort `high` ou plus.

Ordre d'une grosse fonctionnalité : product-owner → architect + ux-designer → frontend-dev et backend-dev (en parallèle une fois le contrat fixé) → qa → reviewer. Un agent ne peut pas me parler : il rend ses questions à la session principale, qui me les pose.

## Skills

N'invoquer que celui qui sert directement la tâche, jamais de chaîne de skills de process pour un petit correctif. Chaque agent liste les siens.
