# ADR-0002: Inlay Hints Token Positioning and IdSelector Dispatch

## Status
Accepted

## Context
Inlay hints in the VS Code editor provide interactive feedback by displaying evaluated RDF terms, references, and function results directly inside the mapping file.

Two primary challenges were identified:
1. **Cursor Collision for Nested AST Nodes**: When requesting evaluation using `CursorSelector(line, character)`, parent and child nodes starting at identical coordinates (e.g. `SubjectMap`, `Template`, and `ReferenceSegment`) collided. Heuristic depth selection could resolve the wrong AST target.
2. **Token Boundary Placement**:
   - For whole literal values (`rml:reference "$.name"`), hints were previously rendered *inside* literal quotes (`"$.name ⇾ "Alice""`).
   - For `rml:template`, the inline hint displayed only the first placeholder value rather than the complete escaped RDF term, and placeholders `{$.Name}` inside the template lacked inline segment feedback.

## Decision Drivers
- **Exact Evaluation Target**: Evaluation dispatch must unambiguously target the specific AST node without coordinate-based guesswork.
- **Visual Clarity & Placement**:
   - Standalone reference and template hints must be placed **outside closing quotes** (`"..." ⇾ ...`).
   - Reference placeholders within templates must be placed **inside the curly braces** (`{$.Name ⇾ "Alice"}`).
   - Templates must generate their full formatted RDF term (e.g. `<http://example.com/Alice>`) at the end of the template literal.

## Considered Options
1. **Coordinate-Based Cursor Dispatch with Heuristic Filters**:
   - *Pros*: Uses existing `CursorSelector`.
   - *Cons*: Highly fragile when multiple AST elements occupy the same line/character span.
2. **Unique ID-Based Dispatch via `IdSelector` & Precise Token Coordinate Mapping**:
   - *Pros*: Unambiguous node evaluation via `PlanNodeIdRegistry`; exact token boundary offsets (`info.start()` to `info.end()` for literals vs `rdfLiteralStringStart().plus(range)` for segments).
   - *Cons*: Requires threading `mappingFileUri` and AST node IDs through JSON-RPC data models.

## Decision
1. **`IdSelector` Evaluation Dispatch**:
   - Updated `IdSelector` to include `mappingFileUri` and `id`.
   - VS Code extension `RmlEvalInlayHintsProvider` evaluates plan nodes directly by `node.id`.
2. **Token Boundary Location Refinement in `ProvTurtleVisitor`**:
   - For `LiteralPart` with `objectRange == null` (entire literal), use `info.start()` to `info.end()` so `node.originRange.end` lands **after closing quotes `"` / `"""`**.
   - For `LiteralPart` with sub-ranges (template segments `{...}`), compute offsets relative to `info.rdfLiteralStringStart()` so `node.originRange.end` lands **inside `{}`**.
3. **Template & Segment Evaluation**:
   - `Template.evaluateNode` delegates to parent `TermGenerator.generateTerms(iter)` to yield formatted RDF terms (with `SAFE_IRI` escaping).
   - `Template.ReferenceSegment` evaluates `reference().getStrings(iter)` to yield local placeholder values.
4. **Position-Based Hint Deduplication**:
   - Deduplication key in `evalInlayHintsProvider.ts` is `${hintPos.line}:${hintPos.character}:${display}`, allowing both segment hints and end-of-template hints to render together seamlessly.

## Consequences
### Positive
- Zero collision between nested AST elements during inlay hint calculation.
- Clean visual placement: hints for literals are outside quotes, hints for template segments are inside `{}`.
- Displays full formatted template RDF terms alongside individual segment evaluations.

### Negative
- Multiple asynchronous evaluate requests are dispatched per document (mitigated by cancellation token handling and client caching).
