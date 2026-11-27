# ADR-0009: Server-Side Inline Evaluation

## Status
Accepted

## Context
Inlay hints provide interactive inline feedback in the editor by displaying evaluated values for references, templates, and function executions directly inside RML mapping files.

The previous client-side implementation (ADR-0002) resolved inlay hints in a two-stage loop:
1. VS Code client called `rml/descendantStructure` to fetch the AST tree.
2. The client traversed the AST and dispatched individual `rml/evaluate` JSON-RPC requests for each node by `node.id`.

This pattern led to several limitations:
1. **Network & Roundtrip Overhead**: Files with numerous mapping nodes generated dozens of sequential or parallel JSON-RPC requests per keystroke/view change, introducing UI latency.
2. **Editor Clutter for Constants**: `rml:constant` terms and shortcut constant predicates/objects (e.g. `rml:predicate foaf:name;` or `rml:constant " - "`) evaluated to the exact literal text already visible in the source, cluttering the code with redundant hints.
3. **Verbose URI Presentation**: Evaluated URIs (e.g., `<http://xmlns.com/foaf/0.1/name>` or `<http://example.com/1>`) were displayed in full expanded form rather than using compact QNames (`foaf:name`, `ex:1`) matching the `@prefix` declarations defined in the file.
4. **Presentation Coupling**: Returning UI-formatted strings (like `" ⇾ ..."` or hardcoded hint kinds) from the server tightly coupled the backend protocol to a specific UI widget rather than providing clean semantic AST evaluation results.

## Decision Drivers
- **Single-Query Efficiency**: Retrieve all inline evaluated items for an entire file (or requested range) in a single RPC query.
- **Zero Evaluation Logic Duplication**: Share `RmlEvaluator.resolveIterations` and `RmlEvaluator.evaluateNodeSafe` across `rml/evaluate`, `rml/trace`, and `rml/inlineEvaluate`.
- **Semantic Data Interface**: Keep the server response purely semantic (`InlineEvaluateItem` containing `id`, `type`, `originRange`, raw `values`, `formattedValues`, and `diagnostics`), leaving presentation formatting (e.g. `" ⇾ "`, tooltips, hint kinds) to the client extension.
- **Intelligent Non-Constant Filtering**: Automatically filter out constant expressions and structural container nodes, emitting items only for dynamic mapping constructs.
- **Prefix-Aware Compaction**: Format evaluated URIs using declared `@prefix` namespaces from the mapping document.
- **Rich Dynamic Context Support**: Support BURP's rich `EvaluationDataContext` (custom reference values, range selection, sample row mock fixtures from the Dev Server UI).

## Considered Options

### Option 1: Keep as-is
- **Description**: Maintain the existing client-side loop.
- **Pros**: No change.
- **Cons**: All of them, keeping a bad design.

### Option 2: Semantic JSON-RPC Endpoint (`rml/inlineEvaluate`)
- **Description**: Provide a single dedicated method `rml/inlineEvaluate` taking `InlineEvaluateParams(mappingFileUri, dataContext, range)` and returning `List<InlineEvaluateItem>`.
- **Pros**:
  - Single network roundtrip per document refresh.
  - More opportunities to optimise evaluation.
  - Returns rich semantic data: AST node ID, node type (`Template`, `ReferenceSegment`, `RawReference`, `FunctionExecution`), token `originRange`, evaluated RDF terms (`values`), and prefix-compacted strings (`formattedValues`).
  - Full first-class support for BURP's rich `EvaluationDataContext` (custom reference values, range selection, raw payload test fixtures from the Dev Server UI).
  - Centralized non-constant AST filtering and prefix compaction in the Java backend.
  - ~~Full presentation decoupling: client extensions retain complete control over tooltip formatting, arrow prefixes, and UI kinds.
- **Cons**:
  -~~ Custom RPC method name (`rml/inlineEvaluate`), requiring the VS Code extension / client to invoke it explicitly.

### Option 3: Standard LSP Protocol (`textDocument/inlayHint` & `textDocument/inlineValue`)
- **Description**: Implement the standard LSP 3.17 `textDocument/inlayHint` and `textDocument/inlineValue` handlers directly on `TextDocumentService`.
- **Pros**:
  - 100% compliant with standard LSP 3.17 specification.
  - Generic LSP clients (VS Code LSP client, Neovim, Emacs, Helix) work out of the box for default sample iterations without custom extension wrappers.
- **Cons**:
  - Standard `InlayHintParams` only accepts `(textDocument, range)` and cannot pass BURP's custom dynamic `EvaluationDataContext` (e.g. user-defined reference mock values from the Dev UI).
  - `textDocument/inlineValue` is primarily designed for active debug sessions (`InlineValueContext` with `frameId`), whereas mapping hints represent live sample evaluation.

## Decision

Adopt **Option 2 (Semantic JSON-RPC Endpoint `rml/inlineEvaluate`)** and defer Option 3 for future standard LSP integration:

1. **Dedicated Endpoint (`rml/inlineEvaluate`)**:
   - Introduce `InlineEvaluateParams(String mappingFileUri, EvaluationDataContext dataContext, Range range)`.
   - Introduce `InlineEvaluateItem(String id, String type, Range originRange, List<RmlEvaluationResult> values, List<String> formattedValues, List<String> diagnostics)` which still keeps the semantic significance of `rml/evaluate` but lighter.

2. **Shared Evaluator Domain Logic**:
   - Introduce `InlineEvaluator` coordinating with `MappingWorkspace.parseAndWireMapping()` and delegating iteration resolution and node evaluation directly to `RmlEvaluator` (`resolveIterations`, `evaluateNodeSafe`).

3. **Non-Constant Node Filtering**:
   - Traverse the AST tree and collect only evaluation targets with an `originRange`:
     - **Included**: `Template`, `Template.ReferenceSegment`, `RawReference`, `FunctionExecution`, and dynamic `TermMap` expressions.
     - **Excluded**: `RDFNodeConstant`, `ExpressionMap` with constant expression, `TriplesMap`, `MappingDocument`, and `AbstractLogicalSource`.

4. **Prefix-Aware Compaction (`RdfFormatter`)**:
   - Capture `@prefix` declarations in `ProvStore` during parsing and expose them via `MappingWorkspace.getPrefixMap(mappingFileUri)`.
   - Compact IRIs to `prefix:localName` (or `:localName` for default prefix) when matching a known namespace; format unmapped IRIs as `<uri>`.
   - Format datatypes with compacted prefixes (e.g. `"25"^^xsd:integer`).

5. **Client-Side Presentation**:
   - Update `RmlEvalInlayHintsProvider` in the VS Code extension to invoke `client.inlineEvaluate()` and map `InlineEvaluateItem` into `vscode.InlayHint` with custom decorations and tooltips based on `item.type`.

## Consequences

### Positive
- **Latency Reduction**: Reduces JSON-RPC traffic from $O(N)$ requests to a single $O(1)$ request per document refresh.
- **Presentation Decoupling**: Server returns clean semantic domain models; extension controls UI presentation and formatting.
- **Cleaner Visual Feedback**: Eliminates redundant hints for static constants while preserving hints for template placeholders, full templates, references, and function results.
- **Compact & Readable URIs**: Uses familiar prefixes declared in the Turtle document.
- **Zero Evaluation Duplication**: Completely reuses `RmlEvaluator` logic without duplicating execution semantics.
- **Full Support for Dynamic Data Contexts**: Enables live switching of sample rows and mock reference values directly from the Dev Server UI.

### Negative
- Generic non-BURP LSP clients will not automatically receive inlay hints until Option 3 is implemented in a future iteration.
