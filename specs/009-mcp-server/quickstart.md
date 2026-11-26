# Quickstart: validate the MCP server

**Feature**: [spec.md](spec.md) | **Contract**: [contracts/mcp-tool.md](contracts/mcp-tool.md)

## Prerequisites

- The backend runs with the same configuration as for the REST search (language model and Google Places keys), see `.env.example`.
- Node.js available, to run the MCP Inspector (a client of the MCP project; run through `npx`).

## 1. Automated checks

```text
mvn -f backend test
```

Expected: all tests pass, including the new MCP tests (tool listing, success, each failure, blank sentence, no credential) and the existing tests unchanged (FR-011).

## 2. Run the backend

```text
mvn -f backend spring-boot:run
```

## 3. Call the tool without any credential (US1, US3)

Open the MCP Inspector, choose Streamable HTTP, URL `http://localhost:8080/mcp` (port to confirm in `application.yml`), no authentication:

1. List the tools: expected exactly one, `search_restaurants`, with `readOnlyHint` true.
2. Call it with `{"sentence": "Je veux manger à opera"}`: expected a result with `criteria` and `deck`, no `zone`, in one response.
3. Call it with `{"sentence": "blablabla"}`: expected an error starting with `NOT_UNDERSTOOD`.
4. Call it with `{"sentence": " "}`: expected an error starting with `SENTENCE_REQUIRED`, and no search in the logs.
5. Compare the success result with `POST /api/search` for the same sentence: same criteria and deck, except that the tool result has no `photoUrl`, `photoAttributions` or `photoSourceUri` (SC-002, FR-014). The REST response still has them.

## 4. Call it from ChatGPT (SC-004)

Needs a public HTTPS URL (research R1, R6) and a Business or Enterprise/Edu workspace where the person may add a custom MCP server.

1. Use the deployed HTTPS backend (the host name is not known to this plan; check that `/mcp` reaches the backend), or a tunnel for a local demo.
2. In ChatGPT, add a custom MCP server with the URL ending in `/mcp` and "No authentication".
3. Ask: "find me an Italian restaurant near Gare de Lyon". Expected: ChatGPT calls `search_restaurants` without asking for confirmation (thanks to `readOnlyHint`) and lists real restaurants.
4. Note the duration of a call and whether ChatGPT cuts it: the limits are not stated by OpenAI.
5. Check that ChatGPT's answer shows no photo and no broken image (FR-014).

Expected results of the test plan are written by the product owner.
