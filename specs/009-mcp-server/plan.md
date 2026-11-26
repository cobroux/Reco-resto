# Implementation Plan: MCP Server for Restaurant Search

**Branch**: `feat/mcp-server` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-mcp-server/spec.md`

## Summary

Expose the existing restaurant search to ChatGPT through an MCP server hosted in the existing Spring Boot backend, over Streamable HTTP at `/mcp`, with no protection (FR-010, product owner). One read-only tool, `search_restaurants(sentence)`, calls the existing `SearchService.search` (position absent, zone not requested) and returns the `SearchResult` unchanged. The photo fields (`photoUrl`, `photoAttributions`, `photoSourceUri`) are removed from the tool result only (FR-014); the REST search keeps them. Failures keep the existing `SearchErrorCode` names, plus one new code for a blank sentence. Research confirmed that the spec is compatible with ChatGPT's documented requirements, with two points: the public URL must be HTTPS (the deployment is, per the product owner) and the tool must declare `readOnlyHint` (added as FR-013). Details: [research.md](research.md).

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1 (existing)

**Primary Dependencies**: existing `spring-boot-starter-webmvc`; new `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` (version 2.1.0 or 2.0.3, fixed by T001, see research R2). No Spring AI model starter.

**Storage**: N/A (no state)

**Testing**: JUnit 5 and Spring MVC test support already used (`@WebMvcTest`, `@MockitoBean`); MCP Inspector for the manual check

**Target Platform**: the existing backend service; ChatGPT web as the client

**Project Type**: web service (backend only; no frontend change)

**Performance Goals**: SC-001, no more than the existing search time plus 1 s

**Constraints**: no change to the existing search (FR-011); no Spring AI chat model pulled in; ChatGPT tool-call limits not stated by OpenAI

**Scale/Scope**: one tool, one endpoint

## Constitution Check

*GATE: passed before research; re-checked after design.*

| Principle | Status |
|-----------|--------|
| I. Spec-Driven Development | Pass. One feature, branch `feat/mcp-server`, folder `specs/009-mcp-server/`. A teammate other than the author must approve spec and plan before `/speckit-tasks`. |
| II. Test-First | Pass. Tasks will put each test before its code (see Test plan). |
| III. Never Guess | Pass with flags. FR-010 decided by the product owner. Values not verified are listed under Open points; none is silently assumed. |
| IV. DRY | Pass. Reuses `SearchService`, `SearchRequest`, `SearchResult`, `SearchErrorCode`, `SearchException`; extends `Restaurant` with `withoutPhoto()` next to `withLocation()` instead of adding a parallel type; nothing replaced (research R3, R4). |
| V. English and Conventional Commits | Pass. |

Technology constraints: Java 21, Spring Boot 4.1.1, base package `hr.emocio.recoresto`, backend only. The new dependency needs no secret; `.env.example` is unchanged unless T001 finds a new property to document.

## Project Structure

### Documentation (this feature)

```text
specs/009-mcp-server/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── mcp-tool.md
├── checklists/
│   └── requirements.md
└── tasks.md              # created by /speckit-tasks, not by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                              # + Spring AI BOM and MCP WebMVC starter
├── src/main/java/hr/emocio/recoresto/search/mcp/
│   └── RestaurantSearchTool.java                         # the tool: calls SearchService, drops photo fields, maps failures
├── src/main/java/hr/emocio/recoresto/search/restaurant/
│   └── Restaurant.java                                   # + withoutPhoto(), next to withLocation()
├── src/main/resources/application.yml                    # + spring.ai.mcp.server.* (protocol, name, version, instructions)
└── src/test/java/hr/emocio/recoresto/search/
    ├── mcp/RestaurantSearchToolTest.java                 # mirrors the package of the class under test
    └── restaurant/RestaurantTest.java                    # withoutPhoto(); added if no test of Restaurant exists
```

**Structure Decision**: the tool sits in a new `search.mcp` package next to `search.api`, because it is another entry point to the same search, as `SearchController` is. Package name to confirm with the team (convention: sub-packages of `search` by role).

## Design

1. `RestaurantSearchTool` has one method annotated as the tool `search_restaurants`, read-only, with the description of [contracts/mcp-tool.md](contracts/mcp-tool.md).
2. It builds `new SearchRequest(sentence, null)` (reuses the blank check, FR-007), calls `searchService.search(request, false)`, and returns a `SearchResult` whose deck restaurants went through `Restaurant.withoutPhoto()` (FR-014). `withoutPhoto()` is the only place that lists the photo fields; the REST controller does not call it.
3. A blank sentence is reported with the code `SENTENCE_REQUIRED`; a `SearchException` is reported with `<CODE>: <message>` using the existing `SearchErrorCode` name. Both are thrown as `RuntimeException`, which the framework converts to an error result (research R2).
4. Logging follows `SearchService`: no full sentence at info level (FR-012); the tool logs only its own call, because `SearchService` already logs the search.
5. Configuration: Streamable HTTP, endpoint `/mcp`, server name and instructions in `application.yml`.

## Test plan (tests first, constitution II)

Backend tests only (`mvn -f backend test`):

1. The server lists exactly one tool, `search_restaurants`, with `readOnlyHint` true and a required `sentence` input (US1 AS1, FR-013).
2. A successful call returns the `SearchResult` of the mocked `SearchService` (criteria and deck unchanged), and the service is called with position `null` and `includeZone = false` (FR-003, FR-006).
2b. In a successful call, no restaurant has `photoUrl`, `photoAttributions` or `photoSourceUri`, while `attributions` and the other fields are kept; `RestaurantTest` checks `withoutPhoto()` on a restaurant with every field set (FR-014).
2c. The REST search still returns the photo fields (existing `SearchControllerTest`, unchanged; FR-011).
3. Each of the five `SearchErrorCode` values gives an error whose text starts with its name, and the result has the error flag set (FR-008, SC-003).
4. A blank or missing sentence gives `SENTENCE_REQUIRED` and `SearchService` is never called (FR-007).
5. A call with no credential header works (US3).
6. The existing tests of `SearchController` and the search stay unchanged and green (FR-011).

SC-002 (same outcome as the REST search for at least 10 sentences, photo fields apart) and SC-004 (ChatGPT) are checked by hand with [quickstart.md](quickstart.md), because they need the real language model and a real ChatGPT workspace.

## Open points (to validate, none assumed)

1. **Routing of `/mcp` in the deployment (research R6).** The deployment is HTTPS (product owner). Its host name and whether `/mcp` reaches the backend are not verified; `.github/workflows/deploy.yml` and `docs/deployment.md` were not read. Checked by the quickstart.
2. **Spring Boot compatibility of Spring AI 2.1.0 (research R2).** Not stated by the pages read; T001 builds the project with the dependency to settle it, and picks 2.1.0 or 2.0.3.
3. **Meaning of "photo fields" (FR-014).** Read as `photoUrl`, `photoAttributions` and `photoSourceUri`; `attributions` (data provider credit) is kept. To validate by the product owner.
4. **Who can connect ChatGPT.** Custom MCP servers are limited to Business and Enterprise/Edu on web; Pro is read/fetch only. Information for the product owner.
5. **Accepted cost risk (FR-010).** No protection; anyone with the URL can trigger paid calls.
6. **Package name `search.mcp`** is a convention proposed by the plan and not yet confirmed by the team. The server name `nappe` is decided.
7. **Tool-call limits of ChatGPT** are not stated by OpenAI; checked in the quickstart.

## Complexity Tracking

No constitution violation; nothing to justify.
