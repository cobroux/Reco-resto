# Feature Specification: MCP Server for Restaurant Search

**Feature Branch**: `feat/mcp-server`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "Expose an MCP (Model Context Protocol) server from the existing backend so that ChatGPT can search for nearby restaurants. Public endpoint at /mcp, over HTTP. A single tool, `search_restaurants`, takes one input, `sentence` (a natural-language sentence; the place is written in the sentence, no position input), runs the same search as the existing search of feature 002 and returns its result as-is, without the photo fields (added 2026-11-26). The caller makes the request, then reads the result in the same response. The public endpoint has no protection (decided 2026-11-26, FR-010). Whether ChatGPT's remote MCP constraints match this design must be verified against official documentation during planning." Decided by the product owner on 2026-11-26.

**Feature set**: this spec adds a second way to reach the search of feature 002 (natural-language search, completed by 008, Google Places source). It does not change that search, its rules or its response, and does not edit the specs of other features.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask ChatGPT for restaurants and get real ones (Priority: P1)

A person talks to ChatGPT, which has been connected to Reco-Resto, and says "find me an Italian restaurant near Gare de Lyon open tonight". ChatGPT calls the Reco-Resto search tool with that sentence, reads the restaurants returned in the same response, and presents them to the person.

**Why this priority**: this is the whole purpose of the feature; without it nothing else has value.

**Independent Test**: connect an MCP-compatible client (ChatGPT or a test client) to the public endpoint, call the search tool with "Je veux manger à opera", and check that restaurants come back in that same response.

**Acceptance Scenarios**:

1. **Given** a client connected to the MCP endpoint, **When** it lists the available tools, **Then** it sees exactly one tool, named `search_restaurants`, with a description telling that it finds restaurants from a natural-language sentence (place included, France only) and that its only input is `sentence`.
2. **Given** a sentence understood by feature 002, **When** the client calls `search_restaurants` with it, **Then** the response contains the same search result as the existing search of feature 002 would return for that sentence (same criteria, same restaurants, same fields), with no second call needed to read it.
3. **Given** a sentence, **When** it is submitted through the tool and through the existing search, **Then** the rules of feature 002 apply identically (radius, open at the targeted moment, deck size, several places merged, France only).
4. **Given** the tool is called without `sentence`, or with a blank one, **When** the call is handled, **Then** the client receives a clear error saying a sentence is required, and no search is run.

---

### User Story 2 - Understand why a search returned nothing (Priority: P1)

ChatGPT sends a sentence the search cannot satisfy (not a restaurant request, a place outside France, no place given, nothing matching, or the search temporarily unavailable). The person must be told something accurate, not receive an empty or broken answer.

**Why this priority**: ChatGPT relays the outcome to a person; an ambiguous failure produces wrong or invented answers.

**Independent Test**: call the tool with one sentence per failure case of feature 002 and check that each returns a distinct, readable error outcome.

**Acceptance Scenarios**:

1. **Given** a sentence that is not a restaurant request, **When** the tool is called, **Then** the response states the sentence was not understood and invites rephrasing.
2. **Given** a place outside France, **When** the tool is called, **Then** the response states that only France is available for now.
3. **Given** a sentence without any place, **When** the tool is called, **Then** the response states that a place is required.
4. **Given** a sentence for which no restaurant matches, **When** the tool is called, **Then** the response states that no restaurant matches the search.
5. **Given** the restaurant data source or the language model is unavailable, **When** the tool is called, **Then** the response states that the service is temporarily unavailable.
6. **Given** any of these failures, **When** the client reads the response, **Then** it can tell it is an error (not a successful empty result) and which of the five cases it is.

---

### User Story 3 - Reach the endpoint without any credential (Priority: P2)

The product owner decided on 2026-11-26 that the public endpoint has no protection. A caller (ChatGPT or any MCP client) connects with no key, no login and no call limit, and the search runs normally.

**Why this priority**: it is the accepted access model of the feature; it is P2 because it adds no behavior to build, only a behavior to preserve and a risk to record.

**Independent Test**: call the endpoint with no credential at all and check that the tool is listed and the search runs.

**Acceptance Scenarios**:

1. **Given** a caller with no credential, **When** it connects to the endpoint and lists the tools, **Then** the tool is listed.
2. **Given** a caller with no credential, **When** it calls the tool, **Then** the search runs normally.
3. **Given** the same caller makes many calls in a row, **When** each is handled, **Then** none is refused by this feature (no limit is applied).

---

### Edge Cases

- A very long sentence: it is handled like in the existing search; no new limit is introduced by this feature unless the plan finds that one is required.
- Many calls at the same time: each is handled independently; no call is refused by this feature (FR-010).
- A client that does not follow the MCP protocol (for example a plain browser visit to the endpoint): it receives a protocol-level refusal, not a search result.
- A client asking for a tool that does not exist: it receives an unknown-tool error.
- The existing search of feature 002 and its web screens keep working unchanged while the MCP endpoint is in use.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose an MCP server on a public endpoint reachable over HTTP, at the path `/mcp`.
- **FR-002**: The server MUST expose exactly one tool, named `search_restaurants`, whose only input is `sentence`, a natural-language sentence in which the place is written.
- **FR-003**: The tool MUST NOT accept a position (latitude and longitude); the place is always derived from the sentence.
- **FR-004**: The tool MUST run the same search as feature 002, so that the same sentence gives the same outcome through the tool and through the existing search.
- **FR-005**: On success, the tool MUST return the search result as it is returned by the existing search (criteria and deck of restaurants), without renaming or reshaping its fields, in the same response as the request (no second call to read it). The only difference is FR-014.
- **FR-014**: The tool MUST NOT return the photo fields of the restaurants (the photo, its credits and its source link), because a photo address of the search is relative to the Reco-Resto site and an assistant cannot use it (decided by the product owner on 2026-11-26). This applies to the tool only: the existing search of feature 002 and its screens keep returning the photo fields. The other fields, including the data provider credits, are returned.
- **FR-006**: The tool MUST NOT return the extra restaurant zone that the existing search can optionally return; only the deck is returned.
- **FR-007**: A missing or blank `sentence` MUST be refused with a clear error and MUST NOT trigger a search.
- **FR-008**: Each failure of feature 002 (not understood, no result, outside France, place required, service unavailable) MUST be returned as an error outcome that the client can tell apart from a successful result and from the other failures, with a readable message.
- **FR-009**: The tool description and input description MUST be written so that an assistant knows when to call it, what to put in `sentence`, and that only France is covered.
- **FR-010**: The public endpoint has no protection: no key, no authentication and no limit on the number of calls (decided by the product owner on 2026-11-26, option A). Any caller that can reach the endpoint can run searches, and each search costs one language-model call and one restaurant-data call. The accepted risk is recorded in the Assumptions.
- **FR-011**: Adding this endpoint MUST NOT change the behavior, response format or performance rules of the existing search of feature 002, nor of the screens that use it.
- **FR-012**: Every call to the tool MUST be logged without recording the full sentence at default log level, consistent with the existing search logging.
- **FR-013**: The tool MUST declare itself as read-only, so that an assistant such as ChatGPT can call it without asking the person for a confirmation (added during planning, after reading OpenAI's documentation: a tool not declared read-only is treated as a write action).

### Key Entities *(include if feature involves data)*

- **Search tool call**: one request from an MCP client, made of one sentence; it produces either a search result or an error outcome. Nothing is stored between calls.
- **Search result**: the result already defined by feature 002 (search criteria understood from the sentence and the deck of restaurants); reused as it is, not redefined here, except that the photo fields are left out (FR-014).
- **Error outcome**: one of the five failure cases of feature 002 plus the blank-sentence case, each with a readable message.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For the reference sentence "Je veux manger à opera", a connected assistant receives restaurants in a single response, with no more than the time the existing search takes for the same sentence plus 1 s.
- **SC-002**: For a set of at least 10 reference sentences covering success and each failure case, the tool returns the same outcome as the existing search for 100% of them, apart from the photo fields that the tool leaves out (FR-014).
- **SC-003**: 100% of failure cases return an error outcome that is distinguishable from a successful result and from the other failures.
- **SC-004**: A person can ask ChatGPT, connected to the server, for restaurants near a named place and get a list of real restaurants from Reco-Resto in a single conversation turn.
- **SC-005**: After the feature is released, the features of the web application (search, swipe deck, card details) keep all their existing tests passing and show no behavior change.

## Assumptions

- The search of feature 002, as completed by feature 008, is the single source of truth for restaurants; this feature reuses it and adds no restaurant logic.
- The endpoint is public and unprotected by the product owner's decision of 2026-11-26 (FR-010, option A). Accepted risk: anyone who finds the URL can trigger paid language-model and restaurant-data calls; the budget is not capped by this feature. The team can add a key or a call limit in a later feature.
- The sentence is in French or any language the existing search already understands; no new language handling is added.
- The tool is read-only: it never creates, changes or deletes data.
- ChatGPT's constraints for connecting to a remote MCP server (supported transport, authentication it accepts, required plan, public reachability) are not verified yet. They MUST be checked against official documentation during planning, and the plan MUST report any mismatch with this spec instead of assuming compatibility. Because the answer may affect FR-001 and FR-010, a mismatch may require amending this spec.
- Reaching the endpoint from ChatGPT requires it to be reachable from the Internet; the way it is deployed or exposed (existing deployment, tunnel for a demo, local only) is outside this spec and handled in the plan.
- The photo fields are the photo address, its credits and its source link; the data provider credits are not photo fields and are kept. This reading of "photo fields" is to be validated by the product owner.
- The deployed backend is served over HTTPS (stated by the product owner on 2026-11-26); the host name is not needed by this feature.
- Test plan expected results will be written by the product owner, as for the previous features.
