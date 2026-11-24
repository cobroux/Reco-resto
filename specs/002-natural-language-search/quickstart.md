# Quickstart: validating the natural-language search

Prerequisite: the backend setup chore is done. Restaurants come from the LLM with web access (see research.md R2), so
"every restaurant is open at that moment" can only be checked against the data returned by the LLM,
not against a reference source. The web search provider variables are to be added to `.env.example`
once the provider is chosen (O10).
Contract: [contracts/search-api.openapi.yaml](contracts/search-api.openapi.yaml). Model: [data-model.md](data-model.md).

## Setup

1. Copy `.env.example` to `.env` and fill `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` (key supplied separately by the team; never commit `.env`).
2. Start the backend: `mvn -f backend spring-boot:run`.

## Automated checks

- `mvn -f backend test` (LLM and restaurant source faked; must pass before any PR).
- Opt-in reference-sentence run against the real LLM for SC-003 (reference set to be written, O4).

## Manual scenarios (map to the spec)

| Scenario | Request | Expected |
|----------|---------|----------|
| US1-1 | "J'aimerais manger asiatique et/ou végétarien dans la zone de Opéra à Paris" | criteria: cuisine asian, diet vegetarian, OR, place "Opéra, Paris", moment now |
| US1-2 | "asiatique et végétarien près d'Opéra" | AND; every restaurant Asian and vegetarian-friendly |
| US1-4/5 | no distance / "à moins de 10 minutes à pied" | radius 2.5 km / the stated distance |
| US1-6/7 | "samedi midi" / no moment | open at that moment / open now |
| US1-8/9 | many / few matches | exactly 15 / all |
| US2-2/3 | "un bon thaï ce soir" with then without position | position used / `PLACE_REQUIRED` |
| US3-1 | "blablabla" | `NOT_UNDERSTOOD` |
| US3-2 | valid sentence, no match | `NO_RESULT` |
| US3-3 | place outside France | `OUTSIDE_FRANCE` |
| US3-4 | LLM or API Adresse down | `503` `SERVICE_UNAVAILABLE`; same request again succeeds once service is back |
