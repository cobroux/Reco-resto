# Research: Natural-Language Restaurant Search

## R1 - LLM for sentence understanding

- **Decision**: use the team-provided LLM: model `nova-lite`, exposed through an OpenAI-compatible
  `POST {base-url}/chat/completions` endpoint (the base URL given by the team already ends with `/v1`,
  as in the OpenAI SDK sample), authenticated with an API key supplied separately.
- **Configuration**: `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` (default `nova-lite`) in `.env`, listed
  without values in `.env.example`. The key and the endpoint URL are not written in the repository.
- **Integration**: the reference snippet received from the team is JavaScript (OpenAI SDK). The backend
  is Java, so it calls the same HTTP API directly; request shape is `{model, messages}`.
- **Rationale**: chosen and provided by the team; OpenAI-compatible, so the client stays small and the
  provider can be swapped by configuration.
- **Not verified** (no key available to the author): support of JSON mode / structured output / tool
  calling (now REQUIRED, see R2), latency, rate limits, context length, French quality. The LLM now also
  extracts the criteria in the same exchange (`SearchAnswer`); the reply is validated against the schema in
  `data-model.md`; an invalid reply is "cannot be understood" (spec, User Story 3, scenario 1). To be
  checked by the developer (O8).
- **Alternatives considered**: none evaluated; the team supplied this endpoint.

## R2 - Restaurant data source

- **Decision (2026-11-25, from the team; supersedes the 2026-11-24 "LLM provides restaurants" and
  "headless Chrome" decisions)**: restaurants come from **Overpass (OpenStreetMap)**. "OpenAlacarte" from
  the spec is not used.
- **Mechanism (2026-11-25, from the team)**: the LLM searches by **tool calling**. The backend declares an
  Overpass tool to the LLM; when the LLM calls it, `OverpassClient` runs the query and the result goes back
  to the LLM, until it gives its final answer (`SearchAnswer`).
- **Tool arguments (2026-11-25, from the team)**: the LLM does not write Overpass QL (it forgot the
  `[out:json]` header and was refused). It sends JSON in the format of the tag filters of the Overpass
  documentation (criteria/per_tag): `around` (points with radius in meters), `tags` (each `key`+`value` for
  `key="value"`, or `key`+`regex` for `key~"regex"`, all must match), optional `types`. The backend builds the
  query (`OverpassQueryBuilder`: `[out:json][timeout:N]`, `out center`), escapes the literals and refuses
  invalid arguments with a message for the model. Only the two filter forms documented on that page are
  supported (no key-exists, no `!=`, no case-insensitive flag). Which OSM tags express a cuisine or a diet is
  left to the model and not verified (O13).
- **Design consequence**: a `RestaurantSource` port returns restaurants for a search; the adapter drives
  the LLM tool-calling loop. The backend never trusts the LLM for what it can recompute (radius, distance,
  opening hours at the moment, address present) and drops invalid items; a missing field stays absent.
- **Bounds on the tool (code in the working tree, values NOT validated by the team)**: query length,
  answer size, number of calls per search, HTTP timeout (`overpass.*`). A query refused or an unusable
  answer is returned to the LLM as a message (`OverpassRefusal`) so it can correct itself; a service
  failure is `SERVICE_UNAVAILABLE`. The query must contain `[out:json`.
- **Instances**: public instances refuse anonymous default clients (HTTP 406 seen on 2026-11-25), so the
  client sends a generic `User-Agent`. A local instance with the Geofabrik France extract is provided in
  `infra/overpass` (about 5 GB download; disk, memory and build time not measured). Which instance each
  environment uses, and the usage policy of the public ones: NOT decided (O11).
- **Development fixture**: `overpass.fixture-file` replaces the network call with a file (same answer for
  every query, data NOT real). Development only (O14).
- **Consequences**: (1) the endpoint MUST support tool calling; not verified (O8). (2) Several LLM turns
  per search weigh on SC-001 (threshold deferred). (3) Overpass results are untrusted text and are passed
  to the LLM as data only; a query is written by the LLM, so the bounds above are the protection against
  abuse of the Overpass instance. (4) OSM completeness: fields such as opening hours, diets, phone, photo,
  menu, signature dish and price level may be missing or absent from OSM altogether; not verified (O13).
- **Fallback if tool calling is not supported**: to decide with the team, not assumed.
- **Alternatives considered**: LLM-only data and headless-Chrome web tool (dropped by the team);
  a real restaurant API (rejected by the team).

## R3 - Place resolution

- **Decision (2026-11-25, from the team)**: the LLM resolves the place (e.g. "Opéra, Paris") and detects a
  place outside France (FR-010), handles several places (FR-015), and returns the search points
  (`SearchAnswer.centers`). The API Adresse client, `PlaceResolver` and `ResolvedPlace` are removed.
- **Risk**: coordinates produced by a language model may be wrong. The validation rule for coordinates
  and for "in France" is NOT defined here (O12).
- **Not verified**: how well `nova-lite` locates landmarks such as "Opéra" or "gare de Lyon", and how
  reliably it flags places outside France.

## R4 - Opening-hours filtering at a targeted moment

- **Decision**: the LLM returns the moment as structured data (see data-model.md); the backend resolves
  it to a concrete date-time using the Europe/Paris time zone and filters restaurants against their
  weekly hours (FR-007). A moment in the past is rejected as not understood (spec edge case).
- **Open detail**: time zone is an inference from "France only"; confirm with the team. A relative
  moment such as "ce soir" needs a defined time (e.g. 19:00 or 20:00); this business value is NOT
  decided and MUST be given by the team.

## R5 - Ranking and relevance

- **Decision**: NEEDS CLARIFICATION. The spec requires "most relevant first" without defining
  relevance (distance, number of matched criteria, data-source rating?). The rule MUST be defined by the
  team; tests need a deterministic rule.

## R6 - Session criteria (FR-014)

- **Decision (2026-11-24, from the team)**: stateless backend. The criteria are returned in every
  response; the client keeps them for the session and sends them back when needed (features 003, 005).
- **Consequence**: the request MUST be able to carry criteria instead of a sentence for later
  refinement; that shape belongs to features 003/005 and is not added here.

## R7 - Privacy

- **Decision**: NEEDS CLARIFICATION (O5). Data leaving the backend: the sentence and the position
  (LLM endpoint), Overpass queries written by the LLM, which contain coordinates (public Overpass instance,
  or none if the local instance is used). The team must validate each third party before release.
