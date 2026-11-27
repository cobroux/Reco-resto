# Data Model: Home Search Screen

## Search sentence

The only input of the screen (spec, Key Entities).

| Field | Type | Rules |
|-------|------|-------|
| `sentence` | string | Free text. Considered empty when it is empty or only whitespace (FR-004). The original value, untrimmed, is what is submitted (FR-005; to confirm, see plan open point 4). No maximum length defined yet (plan open point 1). |

## Screen state (in memory, not persisted)

| State | Type | Meaning |
|-------|------|---------|
| `sentence` | string signal | Current field value. Empty on every opening of the screen. |
| `searching` | boolean signal | `true` between submission and the end of the search; blocks a second submission (FR-006). |
| `emptyError` | boolean signal | `true` after an empty submission; shows "Votre recherche est vide. Ex. : asiatique végétarien près d'Opéra". Reset when the user types. |

## State transitions

```text
idle --submit(non-empty)--> searching --search ends--> idle
idle --submit(empty)------> idle + emptyError
searching --submit--------> searching (ignored)
idle + emptyError --type--> idle
```

Behavior after the search ends is a proposal to confirm (plan open point 3).

## Static content

Four dietary tiles, decorative: Vegan, Végétarien, Sans gluten, Halal, casher… (label, background color, icon color). Defined once in `home.texts.ts` or a sibling constant, used by the template.
