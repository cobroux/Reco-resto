# Contract: MCP endpoint and tool

**Feature**: [spec.md](../spec.md) | **Date**: 2026-11-26

## Endpoint

| Item | Value |
|------|-------|
| Path | `/mcp` (FR-001) |
| Protocol | MCP over Streamable HTTP |
| Authentication | None (FR-010) |
| Public URL scheme | HTTPS when reached by ChatGPT (research R1, R6); the deployment is HTTPS (product owner, 2026-11-26) |

## Server metadata

| Item | Value |
|------|-------|
| `name` | `nappe` (decided by the product owner on 2026-11-26) |
| `instructions` | Short text, first 512 characters self-contained: Nappe finds restaurants in France from a natural-language sentence; call `search_restaurants` when the person wants a place to eat. |

## Tool `search_restaurants`

Annotations: `readOnlyHint = true` (the tool never modifies anything; research R1).

Description (FR-009), to be finalized in the tests: finds restaurants from a natural-language sentence that contains the place, for example "an Italian restaurant near Gare de Lyon open tonight". Use it when the person wants somewhere to eat. France only. Returns the understood criteria and a deck of restaurants.

Input schema:

```json
{
  "type": "object",
  "properties": {
    "sentence": {
      "type": "string",
      "description": "What the person wants, in natural language, with the place written in the sentence. France only."
    }
  },
  "required": ["sentence"]
}
```

Success: the `SearchResult` of `specs/008-google-places-source/contracts/search-api.openapi.yaml`, without `zone`, and without the photo fields `photoUrl`, `photoAttributions` and `photoSourceUri` on each restaurant (FR-014).

Failure: an error result whose text is `<CODE>: <message>`, with `<CODE>` one of the six codes of [data-model.md](../data-model.md).

## Not in the contract

- No second tool, no resource, no prompt.
- No position input, no `includeZone` input.
- Tool-call time and size limits of ChatGPT: not stated by OpenAI; verified in the quickstart.
