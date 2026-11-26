# Quickstart: validating Restaurant Card Details

## Prerequisites

- `npm --prefix frontend install`
- Backend running for real data (`mvn -f backend spring-boot:run`, see `.env.example`), or the stub deck.

## Automated

```text
npm --prefix frontend test -- --watch=false
```

Expected: all specs pass, including the card (flip, sections hidden without data, preview, tap vs drag), `weekly-hours`, `opening`, `deck-input` (menu and phone mapped), the stub route preview, and the deck logo link.

## Manual scenarios

Run `npm --prefix frontend start`, search from the home page, open the deck.

1. **Flip (US1 AS1-2)**: press "↻" or tap the card: the back shows; again: the front shows.
2. **Content (US1 AS3)**: header, keywords, "Horaires" with the targeted day highlighted, "Adresse" with "Itinéraire", "Menu".
3. **Scroll (US1 AS4)**: with a long menu, the back scrolls inside the card.
4. **Missing data (US1 AS5-6, SC-001)**: a restaurant without menu shows no "Menu"; without hours, no "Horaires".
5. **Responsive (US1 AS7)**: check at 1280 px and 375 px.
6. **Preview (US2 AS1-2, SC-002)**: "Itinéraire" shows the times; react to the card: the deck goes on.
7. **Full route (US2 AS3-4)**: from the preview, open the route screen, come back: same card, reactions kept.
8. **Logo**: press the logo at the top-left of the deck: the home page opens.

Travel times come from the stub until feature 006.
