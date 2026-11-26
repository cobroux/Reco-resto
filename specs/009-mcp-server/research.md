# Research: MCP Server for Restaurant Search

**Feature**: [spec.md](spec.md) | **Date**: 2026-11-26

Every entry below states its source. "Not stated" means the page read does not say it; nothing was assumed.

## R1 - What ChatGPT requires of a remote MCP server

Sources read: [Add custom MCP server](https://developers.openai.com/api/docs/guides/developer-mode) (page D) and [Connecting custom MCP servers in ChatGPT](https://help.openai.com/en/articles/12584461-developer-mode-and-mcp-apps-in-chatgpt) (page H), read on 2026-11-26.

| Question | Finding | Impact on this spec |
|----------|---------|---------------------|
| Transport | D: "Supported MCP protocols: SSE and streaming HTTP." | Streamable HTTP (FR-001) is supported. |
| URL scheme | H: "enter your server's public HTTPS MCP endpoint URL". Plain HTTP: not stated. | The endpoint reached by ChatGPT must be HTTPS. TLS is a deployment concern, not an application one (see R6). |
| Local server | H: "Not directly. ChatGPT connects to remote MCP servers." | A local run needs a tunnel to be reached from ChatGPT. |
| Authentication | D: "OAuth", "No authentication", "OAuth or no authentication". API key, custom header, bearer token: not stated. | FR-010 (no protection) matches the "No authentication" option. No mismatch. |
| Plans and roles | H: custom MCP servers available on Business and Enterprise/Edu, ChatGPT web only; Business: only admins and owners create the connection; Pro: read/fetch permissions only. | Not a code constraint, but the people who can connect ChatGPT to Reco-Resto are limited; to tell the product owner. |
| Read-only detection | D: ChatGPT respects the `readOnlyHint` annotation; a tool without it is treated as a write action, which by default asks for confirmation. | The tool MUST declare `readOnlyHint = true` (new requirement FR-013 in the plan; the tool is read-only per the spec Assumptions). |
| Tool descriptions | D (guidance, not a rule): action-oriented names and descriptions with "Use this when…"; keep the first 512 characters of the server `instructions` self-contained. | Feeds FR-009. |
| Time or size limit of a tool call | Not stated on either page. | Unknown; checked by the quickstart (a real search takes several seconds). |

Decision: the design of the spec is compatible with ChatGPT. Two points are reported rather than assumed away: HTTPS (R6) and the read-only annotation (above).

## R2 - How to expose the MCP server from the Spring Boot backend

Sources: [Streamable-HTTP MCP Servers](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html) and [MCP server annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html), both the Spring AI 2.1.0 reference; Maven Central metadata read on 2026-11-26 (stable `2.1.0`, also `2.0.3`).

- Dependency: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`. The project already uses Spring MVC (`spring-boot-starter-webmvc`), so the WebMVC variant fits.
- Protocol: `spring.ai.mcp.server.protocol=STREAMABLE`. Endpoint: `spring.ai.mcp.server.streamable-http.mcp-endpoint`, default `/mcp`.
- Tool: `@McpTool(name, description, annotations = @McpTool.McpAnnotations(readOnlyHint = true, ...))`; parameter: `@McpToolParam(description, required)`.
- Errors: "A `RuntimeException` is converted to an error `CallToolResult` and conveyed to the model"; a checked exception or an `Error` fails the call and is not conveyed. The page does not say whether that result has `isError` set: the contract test of the plan MUST assert it.
- Server name, version, `instructions`: `spring.ai.mcp.server.name`, `.version`, `.instructions`.

Not stated by the pages read, so to be verified by the first task (a build and a test, not a guess):
1. The Spring Boot version that Spring AI 2.1.0 targets, against this project's Spring Boot 4.1.1. A secondary source paired Boot 4.1.1 with Spring AI 2.0.1; it is not authoritative.
2. Whether the BOM to import is `org.springframework.ai:spring-ai-bom` (name from general knowledge of the project; not stated on the pages read).
3. Whether the MCP starter works without a Spring AI model starter. The pages show no model starter in the dependency examples. The project has its own language-model client (`LlmChatClient`), so a Spring AI chat model MUST NOT be pulled in.

Decision: use `spring-ai-starter-mcp-server-webmvc` with Streamable HTTP, annotations for the tool, in the existing backend (the product owner's decision). The version (2.1.0 or 2.0.3) is fixed by task T001 once the Spring Boot compatibility is checked.

Alternatives considered:
- The official MCP Java SDK directly: more code (transport, session, schema) for no benefit here.
- A separate service: rejected by the product owner (the MCP server lives in the backend).

## R3 - Reusing the existing search (DRY)

Searched the code base by behavior (`search`, `SearchRequest`, `SearchResult`, `SearchErrorCode`, `SearchException`, `includeZone`).

- `SearchService.search(SearchRequest, boolean includeZone)` is the single search. The tool calls it with `new SearchRequest(sentence, null)` and `includeZone = false` (FR-003, FR-004, FR-006). Reused as is.
- `SearchRequest`'s constructor already rejects a blank sentence with `IllegalArgumentException`. The tool reuses it for FR-007; no second blank check.
- `SearchResult` is serialized by Jackson as the REST API does (`@JsonInclude(NON_NULL)`). Reused as is (FR-005), with the photo fields removed from each restaurant of the deck (FR-014, R4).
- `SearchException` and `SearchErrorCode` already name the five failures (`NOT_UNDERSTOOD`, `NO_RESULT`, `OUTSIDE_FRANCE`, `PLACE_REQUIRED`, `SERVICE_UNAVAILABLE`). The tool turns a `SearchException` into an error whose message carries the code name and the existing message. Nothing is duplicated.
- `ApiExceptionHandler` maps `SearchException` to an HTTP body for REST; it does not apply to MCP tool calls, so the MCP mapping is a separate adapter over the same codes.

Nothing to replace or remove.

## R4 - Relative photo URL (finding, decided by the product owner)

`Restaurant.photoUrl` returned by the search is a relative path (for example `/api/photos/p1/ph1`, see `SearchControllerTest`). Through REST it resolves against the site host. A ChatGPT client has no host to resolve it against.

Decision (product owner, 2026-11-26): the tool does not return the photo fields (FR-014); the REST search keeps them. The photo fields of `Restaurant` are `photoUrl`, `photoAttributions` and `photoSourceUri` (see the record's Javadoc, which groups the last two with spec 008). `attributions` is the data provider credit, not a photo field, and is kept (reading to validate, see the spec Assumptions).

DRY: `Restaurant` already has a copy method for one change (`withLocation`). A sibling `withoutPhoto()` on `Restaurant` is the single place that knows which fields are photo fields. Alternatives rejected: a Jackson mix-in or a second DTO, which would repeat the list of restaurant fields.

## R5 - No protection (FR-010)

Decided by the product owner on 2026-11-26: no key, no login, no limit. ChatGPT's "No authentication" option matches. The accepted cost risk is recorded in the spec Assumptions. No security filter exists in the backend today (searched for `SecurityFilterChain`, `cors`, `allowedOrigins`: none), so nothing blocks the endpoint.

## R6 - Reaching the endpoint from ChatGPT (deployment, outside the application code)

ChatGPT needs a public HTTPS URL (R1). The product owner stated on 2026-11-26 that the deployment is served over HTTPS; `.github/workflows/deploy.yml` and `docs/deployment.md` were not read, so the host name and whether `/mcp` is routed to the backend are not verified. No code task; the quickstart gives a tunnel-based check for a local run and a check against the deployed URL.
