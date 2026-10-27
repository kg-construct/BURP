# ADR-0013: RML Language Intelligence via Language Server Protocol (LSP) Roadmap

## Status
Accepted

## Context
The RML Dev Server (`rml-dev-server`) originally implemented language intelligence features—such as reference completion, inline evaluation, iteration tracing, and SHACL validation diagnostics—over a custom JSON-RPC protocol layered on top of Eclipse LSP4J.

While this bespoke JSON-RPC interface allowed rapid iteration on specialized execution inspection features (such as passing custom data contexts, inspecting dropped iterations, and tracing join condition failures), it created coupling between the VS Code extension client and BURP:
1. **Client Complexity**: Client-side logic in the VS Code extension had to orchestrate custom request payloads and map responses to VS Code UI primitives (e.g. manually transforming custom completions to `vscode.CompletionItem` and hints to `vscode.InlayHint`).
2. **Editor Lock-in**: Other editors and IDEs (IntelliJ IDEA via LSP plugins, Neovim, Helix, Emacs) cannot easily leverage RML language intelligence without reimplementing the custom client adapter.
3. **Duplication with Standard LSP Capabilities**: The Language Server Protocol (LSP 3.17+) already standardizes `textDocument/completion`, `textDocument/inlayHint`, `textDocument/hover`, `textDocument/publishDiagnostics`, `textDocument/definition`, and `textDocument/semanticTokens`.

## Decision Drivers
- **Editor Interoperability**: Enable first-class RML editing intelligence in any LSP-compliant editor (IntelliJ, VS Code, Neovim, etc.) out of the box.
- **Protocol Standardization**: Adhere to LSP specifications for language authoring while preserving custom endpoints for dynamic execution and debugging.
- **Architectural Separation**: Clearly separate static language intelligence (AST analysis, schema inspection, ontology lookups) from interactive runtime execution (tracing, data context evaluation, webview rendering).

## Considered Options

### Option 1: Retain Purely Custom JSON-RPC Protocol
Continue using custom protocol methods (`rml/completeReference`, `rml/validate`, etc.) for all features.
- *Pros*: Maximum freedom to pass domain-specific parameters without fitting into LSP structures.
- *Cons*: Cannot be consumed by standard LSP clients; requires maintaining custom client wrappers across editors.

### Option 2: Migrate All Functionality Strictly to Standard LSP
Force all dev server interactions (including live evaluation and step-by-step tracing) into standard LSP requests or command executions.
- *Pros*: Single protocol for everything.
- *Cons*: Standard LSP is ill-suited for rich execution inspection (such as streaming iteration traces, step-through execution contexts, and Baukasten visualizer graphs).

### Option 3: Hybrid Protocol Architecture (Selected)
Adopt standard LSP interfaces for all static and semi-static language intelligence, while retaining custom `rml/*` JSON-RPC methods for dynamic execution, evaluation, and visual debugging.
- *Pros*: Best of both worlds. Standard LSP clients immediately gain completions, diagnostics, hover, and inlay hints; advanced visual debugging tools continue to use expressive custom RPC methods.
- *Cons*: Requires maintaining both standard LSP endpoint registrations and custom JSON-RPC service interfaces in `RmlLanguageServer`.

## Decision
We adopt **Option 3: Hybrid Protocol Architecture**. We establish a phased roadmap to transition RML language intelligence to standard LSP endpoints in `burp.dev.rml.service` and Eclipse LSP4J:

### Roadmap Phases

#### Phase 1: Standard Diagnostics (`textDocument/publishDiagnostics`)
- Push SHACL validation issues, syntax errors, and static analysis warnings (e.g. missing function parameters, deprecated predicate usages, unused sources) as standard `Diagnostic` notifications.
- Eliminate manual client polling or explicit `rml/validate` triggers for standard error squiggles.

#### Phase 2: Standard Completion (`textDocument/completion`)
- Expose reference formulations (`$.path`, `/xpath`, CSV columns), FnO function IRIs, and FnO parameter predicates through standard LSP `CompletionItem` with `CompletionItemKind.Property`, `CompletionItemKind.Function`, and `CompletionItemKind.Field`.
- Server determines node AST context via `CompletionParams.getPosition()`, dispensing with client-side Turtle syntax heuristics.
- Retain `rml/completeReference` as a transitional fallback until client migrations are complete.

#### Phase 3: Standard Inlay Hints (`textDocument/inlayHint`)
- Migrate reference evaluation previews, parameter names, and function return indicators from `rml/inlayHints` to standard `InlayHint` responses.
- Enables native editor configuration of hint padding, display modes, and click-through navigation.
- Do not remove inlineEvaluate that can be still used by other clients.

#### Phase 4: Hover Tooltips (`textDocument/hover`)
- Implement `textDocument/hover` to display rich Markdown tooltips when hovering over:
  - Logical sources (source file schema, record counts).
  - FnO functions and parameters (ontology description, expected types, required vs. optional status).
  - Reference formulations (sample values from linked datasets).

#### Phase 5: Navigation & Definition (`textDocument/definition`, `textDocument/references`)
- Support go-to-definition for TriplesMap references (`rml:parentTriplesMap`), declared FnO execution blocks (`<#Execution>`), and shared prefix declarations.

#### Dynamic Execution Retention
The following custom JSON-RPC endpoints remain dedicated to BURP execution and visual tooling:
- `rml/evaluate` and `rml/inlineEvaluate`: interactive cursor evaluation with custom data context overrides.
- `rml/trace`: iterative step execution with intermediate output graphs and dropped iteration inspection.
- `rml/execute` and `rml/executeAll`: physical mapping execution and dataset export.
- `rml/structure/ancestor` and `rml/structure/descendant`: plan graph hierarchy exploration for the Baukasten visualizer.

## Consequences

### Positive
- RML editing intelligence becomes portable across IntelliJ, VS Code, and other LSP clients.
- Drastic reduction of client-side code in `rml-dev-server` extension; standard language server client handles completions, diagnostics, and hints automatically.
- Server remains the single source of truth for AST context resolution.

### Negative
- Server must implement and test both LSP4J standard services (`TextDocumentService`) and custom JsonRPC services during the transition period.
- Baukasten UI executions panels are still vscode only.
- Have multiple servers protocols (even if evry similar technically).
