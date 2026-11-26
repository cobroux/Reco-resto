# Quickstart: validating the Google Places source

Validation guide, not an implementation. Contract: [contracts/search-api.openapi.yaml](contracts/search-api.openapi.yaml)
and [contracts/google-places-requests.md](contracts/google-places-requests.md). Model: [data-model.md](data-model.md).
Expected outcomes of the test plan are those written by the product owner in [spec.md](spec.md) (column
Expected); this guide only says how to run each check.

## Prerequisites

- A Google Cloud project with billing and the Places API (New) enabled, and a key restricted to that API (outside
  this feature, spec "Out of scope"). Every real search is billed after the free caps (research R2, plan D17).
- `.env` (never committed) with `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` and `GOOGLE_PLACES_API_KEY`; the names are
  in `.env.example`. On the instance, the deploy workflow writes `GOOGLE_PLACES_API_KEY` from a GitHub secret.
- Open decisions D1-D21 answered and reflected in `application.yml`.

## Automated checks (no key, no cost)

```bash
mvn -f backend test
npm --prefix frontend test -- --watch=false
```

- Google is faked (`MockRestServiceServer`, recorded answers); the LLM is faked with the recorded faulty answers of
  T12-T19 (SC-003).
- Development without a key: `GOOGLE_PLACES_FIXTURE_FILE=<recorded answer>` returns that answer for every search
  (data NOT real; never set in production).

## Run end to end

```bash
mvn -f backend spring-boot:run          # reads GOOGLE_PLACES_API_KEY from the environment
npm --prefix frontend start             # /api proxied to the backend
```

Direct call:

```bash
curl -s -X POST 'http://localhost:8080/api/search?includeZone=true' \
  -H 'Content-Type: application/json' -d '{"sentence":"Je veux manger à opera"}'
```

## Scenarios (map to the spec test plan)

| Check | How | Spec |
|---|---|---|
| Timing and reliability | In the browser, "Je veux manger à opera" 3 times; time from "Commencer" to the first card (browser performance panel, or backend timing logs for the server part) | T46, T47, SC-001, SC-002 |
| Source is Google only | Every restaurant has a `placeId`; stop any Overpass instance: no change | T1, T2 |
| Model writes no query | Debug log of the LLM request: no tools declared; final answer holds criteria only | T3, T4 |
| 002 rules | Sentences of T5-T11 and T38 with curl; check radius, moment, dedup across places, address, France | T5-T11, T38, SC-005 |
| Faulty model answers | Unit tests with recorded answers (fenced, prose, two objects, missing fields, missing/invalid centers, invalid JSON, empty) | T12-T19, SC-003 |
| Not a request | "blablabla" → `422 NOT_UNDERSTOOD` | T20 |
| Card data | Compare each returned restaurant with Google's data for the place (photo, hours, price, website, phone, diets) | T21-T28, SC-004 |
| Photo without key | Inspect `photoUrl` values and the browser network panel: no `key=` and no key value anywhere; break a photo address: tablecloth shown | T29, T30, T39, SC-006 |
| Contract compatibility | Current frontend against the new backend: deck opens; `website` ignored by the deck | T31, T32 |
| Google failures | Unset the key; set a wrong key; point `GOOGLE_PLACES_BASE_URL` to an unreachable host; set a very low timeout: each gives `503 SERVICE_UNAVAILABLE` and "Réessayer"; logs name the cause, `grep` for the key value in logs finds nothing | T33-T37, T39 |
| Attribution | Card front shows the Google attribution (form D15); photo author shown per D15 | T40, T41, SC-007 |
| Maps | 005/006 deferred (FR-015 option C); nothing to check in 008 beyond "no map shown" | T42, T43 |
| Nothing kept | No file/DB written; logs contain place IDs and counts, no names or addresses | T44 |
| Result bound | Many matches around Opéra with `includeZone=true`: zone size ≤ what Google returned (D3) | T45 |
