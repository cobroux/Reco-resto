# Contract: language-model tools and Google Place Details request

## Tool `search_restaurants`

Unchanged: same name, description, parameters (`query`, `price_levels`, `open_at`, `radius_m`, `walking_minutes`, `diets`), same results and the same limits (`search.tool.max-tool-calls`). It only moves into `SearchRestaurantsTool` (spec FR-002). The candidates it returns keep `rating` and `reviews` (the count), now read from `Restaurant.rating` and `Restaurant.ratingCount`.

## Tool `get_place_reviews` (new)

Definition sent to the model (OpenAI function format, like the existing tool):

```json
{
  "type": "function",
  "function": {
    "name": "get_place_reviews",
    "description": "Gives the Google rating, the number of ratings and a few user reviews of one candidate restaurant returned by search_restaurants.",
    "parameters": {
      "type": "object",
      "properties": {
        "id": { "type": "string", "description": "Candidate id given by search_restaurants, for example r3" }
      },
      "required": ["id"]
    }
  }
}
```

The wording of the description and of the system prompt step is reviewed with the plan.

Results (the `content` of the `role:"tool"` message):

| Case | Result |
|---|---|
| Rated restaurant | `{"id":"r3","rating":4.6,"ratingCount":812,"reviews":[{"rating":5,"text":"..."}]}` with at most `max-reviews` reviews |
| Rated restaurant, Google returns no review | `{"id":"r3","rating":4.6,"ratingCount":812,"reviews":[]}` |
| No rating and no review (FR-005) | `{"id":"r3","message":"no rating available"}` |
| Id not in the current candidates, or no search yet | `{"error":"unknown candidate id"}` |
| Google unavailable, quota or key failure (FR-005, SC-002) | `{"id":"r3","error":"reviews unavailable"}` |
| Call over the limit | `{"error":"No more searches: give your final JSON answer now."}` (same text as today) |

No author name, date or profile link is ever included.

## Google Place Details request (new operation of `GooglePlacesClient`)

| Item | Value |
|---|---|
| Method and path | `GET {google-places.base-url}/places/{placeId}` |
| `placeId` | must match `GooglePlaceMapper.IDENTIFIER`; otherwise no request is sent |
| Headers | `X-Goog-Api-Key` (only there, never in a URL), `X-Goog-FieldMask: reviews` |
| Query | `languageCode={google-places.language-code}` |
| Retries and logs | the existing `call("reviews", ...)`: new attempts of D2, WARN with the cause class, never the key |
| 404 | empty list |
| Other failures | `SearchException(SERVICE_UNAVAILABLE)`, caught by the tool |
| Development fixture | empty list, with a WARN that the data is not real (plan open item 6) |
