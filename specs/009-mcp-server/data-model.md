# Data Model: MCP Server for Restaurant Search

**Feature**: [spec.md](spec.md) | **Date**: 2026-11-26

This feature stores nothing and defines no new data type for restaurants. It adds one input and maps existing outcomes.

## Tool input

| Field | Type | Required | Rule |
|-------|------|----------|------|
| `sentence` | text | yes | Not blank (FR-007). The place is written in it (FR-003). Validated by reusing the constructor of the existing `SearchRequest`. |

There is no position and no `includeZone` input (FR-003, FR-006).

## Tool output on success

The existing `SearchResult` (FR-005): `criteria` and `deck`. The `zone` is always absent because the tool always asks for `includeZone = false`. Field names, optional fields and `NON_NULL` omission are those of the REST search (see `specs/008-google-places-source/contracts/search-api.openapi.yaml`); not redefined here.

One difference (FR-014): in every restaurant of the deck the photo fields `photoUrl`, `photoAttributions` and `photoSourceUri` are absent. All other fields, including `attributions`, are unchanged. The REST search is not affected.

## Tool output on failure

An error outcome (FR-008): a readable message that starts with a stable code, then the existing message of the failure.

| Case | Code in the message | Source |
|------|---------------------|--------|
| Blank or missing sentence | `SENTENCE_REQUIRED` | `IllegalArgumentException` of `SearchRequest` (FR-007) |
| Not a restaurant request | `NOT_UNDERSTOOD` | `SearchErrorCode` |
| No restaurant matches | `NO_RESULT` | `SearchErrorCode` |
| Place outside France | `OUTSIDE_FRANCE` | `SearchErrorCode` |
| No place in the sentence | `PLACE_REQUIRED` | `SearchErrorCode` |
| Data source or language model unavailable | `SERVICE_UNAVAILABLE` | `SearchErrorCode` |

`SENTENCE_REQUIRED` is the only code created by this feature. The five others are the existing `SearchErrorCode` names, so no code is duplicated.

An unexpected exception (a bug, not one of the six cases) is not mapped: it is logged and the client receives the generic tool error of the framework, never a stack trace.

## State

None. Each call is independent (spec, Key Entities).
