# UI Contract: Swipe Deck

Routes, screen content, controls and texts. French texts are word for word. Values not in the spec were decided on 2026-11-24 (plan, Decisions D1–D17).

## Routes

| Route | Screen | Owner |
|-------|--------|-------|
| `/deck` (D14) | Swipe deck | 003 |
| `/results` | Placeholder, replaced by 005 | 003 now, 005 later |
| `/route/:id` | Placeholder, replaced by 006 | 003 now, 006 later |

## Deck screen

| Element | Content | Source |
|---------|---------|--------|
| Title | "Ça vous dit, <momentLabel> ?"; "Ça vous dit, maintenant ?" when no moment | FR-001, US1 AS1 |
| Progress | "<position> sur <size>" and a thin progress bar | FR-001, D11 |
| "Voir la liste" | Opens `/results` at any time; desktop in the header; mobile: pill at the right of the progress row | FR-009, D10 |
| "<" | Top-left; label "Revenir à la carte précédente"; rewind; disabled on the first card; no home link | FR-007, US4 AS3, D3, D4 |
| Card front | photo or tablecloth visual; distance; name; cuisine + keywords chips; price level; "Ouvert jusqu'à HH:MM"; "À goûter" + dish name, or default dish visual without name | US1 AS2–AS4 |
| "✕" | Label "Pas envie"; dislike | FR-004, D4 |
| "♥" | Label "Miam"; like | FR-004, D4 |
| "→" | Label "On y va"; pick | FR-004, D4 |
| Degraded message | "Suggestions non personnalisées pour le moment", discreet, non-blocking | FR-013 |
| Not shown | "Vos coups de cœur" panel; card back; "Voir la fiche" | FR-012; feature 004 |

## Gestures

| Gesture | Effect |
|---------|--------|
| Drag/swipe left beyond threshold | Dislike |
| Drag/swipe right beyond threshold | Like |
| Drag/swipe up beyond threshold | Pick |
| Release under threshold | Card returns to its place, no reaction |

Mouse drag and touch behave the same (FR-005). Thresholds (D5): 110 px horizontally and upwards (vertical movement dominant for up), drag starts after 6 px. Mobile layout below 768 px.

## Navigation after reactions

| Situation | Next |
|-----------|------|
| Like/dislike, not last card | Next card |
| Like/dislike on last card | `/results` (FR-010) |
| Pick | `/route/:id` (FR-008) |
| Back from `/route/:id` | Deck, next card, picked counted as liked (FR-014); `/results` if the picked card was the last one (D12) |

## Route placeholder (temporary)

Restaurant name, a button back to the deck (FR-014), a button to `/results` (FR-008). Texts (D13): restaurant name, "Retour aux propositions", "Voir la liste".

## Results placeholder (temporary)

Lists the session reactions (FR-011) so the hand-off to 005 is visible. Texts (D13): "Résultats — bientôt disponible" and the list of recorded reactions.
