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
  calling, latency, rate limits, context length, French quality. Until verified, the extractor asks for
  JSON in the prompt and validates the reply against the schema in `data-model.md`; an invalid reply is
  treated as "cannot be understood" (spec, User Story 3, scenario 1). To be checked by the developer (O8).
- **Alternatives considered**: none evaluated; the team supplied this endpoint.

## R2 - Restaurant data source

- **Decision (2026-11-24, from the team)**: there is no restaurant API; the LLM provides the
  restaurants. "OpenAlacarte" from the spec is not used.
- **Design consequence**: a `RestaurantSource` port returns restaurants around a point and radius with
  the FR-012 fields when available; its only adapter asks the LLM and parses a JSON answer. Every
  answer is validated against the schema; invalid items are dropped.
- **Risk R2-a**: a language model without a search tool answers from its training data and may invent
  restaurants, addresses, phone numbers, menus, prices, opening hours or photo URLs.
- **Decision (2026-11-24, from the team)**: the LLM MUST have web access. Mechanism chosen: the backend
  declares a web search tool to the LLM (tool calling) and executes the searches through a web search API.
- **Consequences**: (1) the endpoint MUST support tool calling; not verified, blocking (O8). (2) A web
  search provider and its access are needed; NOT chosen (O10). (3) Each search costs several LLM turns
  plus web calls, which affects SC-001 (threshold deferred). (4) Web results are untrusted text: the
  prompt MUST treat them as data, and the answer is still validated. (5) Web access reduces but does not
  remove the risk of wrong or stale data (e.g. outdated opening hours), so the validation below stays.
- **Web provider (2026-11-24, from the team)**: headless Chrome driven by the backend. The tool
  executed by the backend opens a search engine and pages in Chrome and returns their text to the LLM.
- **Consequences of headless Chrome**: (1) a Chrome binary and an automation library must be available
  where the backend runs, with enough memory; not assessed. (2) Page loads are slow, which weighs on
  SC-001. (3) Search engines may block or rate-limit automated browsers and may forbid it in their terms
  of use; engine and terms to be checked by the team (O10). (4) The LLM chooses what to open: the tool
  MUST limit allowed URLs and schemes (http/https only, no private or local addresses), page count,
  time and size per search, otherwise the backend can be used to reach internal resources. (5) Page
  content may contain instructions aimed at the LLM; it MUST be passed as data only. Design points to
  confirm in the task list, not decided here.
- **Fallback if tool calling is not supported**: the backend runs the web search itself and injects the
  results into the prompt; to decide with the team, not assumed.
- **Mitigations in the design (no external data needed)**: the backend re-checks what it can (address
  present, distance from the resolved place computed from the API Adresse coordinates of the address,
  opening hours consistent with the targeted moment) and never invents a missing field.
- **Alternatives considered**: a real restaurant data source (e.g. OpenAlacarte); rejected by the team.

## R3 - Place resolution (geocoding)

- **Decision (2026-11-24, from the team)**: API Adresse (data.gouv.fr). Needed to turn "Opéra, Paris"
  into coordinates, to detect a place outside France (FR-010) and to handle several places (FR-015).
- **To verify** (not checked by the author): the API covers France only, so a place outside France is
  expected to give no result; this must be distinguished from a French place that is merely unknown,
  and from a service failure. Whether it resolves landmark-like places such as "Opéra" or "gare de Lyon"
  as well as addresses is not verified.
- **Use for restaurants**: also used to geocode the address of each restaurant returned by the LLM so the
  radius and distance are computed by the backend rather than trusted from the LLM.

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

- **Decision**: NEEDS CLARIFICATION (O5). Data leaving the backend: the sentence and the place or
  position (LLM endpoint), place names and restaurant addresses (API Adresse), search queries derived from
  the sentence (web search provider). The team must validate each third party before release.
