# ADR-0001: RML Dev Server Architecture and JSON-RPC Protocol

## Status
Accepted

## Context
BURP provides developer tooling via the RML Dev Server and a dedicated VS Code extension. To ensure maintainability, clear separation of concerns, and clean developer workflows, foundational decisions were needed regarding protocol design, service layering, TypeScript contract synchronization, and UI technology stack.

## Decision Drivers
- **Standardized Communication**: Protocol between Java backend and VS Code extension should use a well-defined standard (JSON-RPC over sockets) with future compatibility for LSP and MCP protocols.
- **Service Modularization**: Backend responsibilities (document synchronization, evaluation, tracing, completion, structure introspection) must be cleanly decoupled into dedicated services.
- **Type Safety across Languages**: TypeScript definitions for frontend clients should be generated directly from Java model classes to prevent interface drift.
- **Consistent UI Components**: Webview interfaces must use modern, reactive components (React 19 + TypeScript + Baukasten UI design system) without raw HTML string generation.

## Considered Options
1. **Ad-hoc CLI Subprocess Invocations & Inline HTML Strings**:
   - *Pros*: Simple initial prototyping.
   - *Cons*: High latency, difficult synchronization, brittle UI templates, zero type sharing.
2. **Modular JSON-RPC Architecture, Automated TypeScript Code Generation, and Baukasten Design System**:
   - *Pros*: Single persistent server, typed JSON-RPC endpoints (`RmlLanguageServer`), automated TypeScript types via `typescript-generator-maven-plugin`, modular services (`MappingWorkspace`, `RmlEvaluator`, `RmlTracer`, `ReferenceCompleter`, `StructureInspector`), reactive UI using Baukasten UI.
   - *Cons*: Requires maven build plugin configuration and structured service interfaces.

## Decision
1. **JSON-RPC Interface (`RmlLanguageServer`)**:
   - `RmlLanguageServer` defines the public JSON-RPC protocol implemented by `RmlDevServerImpl.java`.
   - Protocol covers `evaluate`, `trace`, `completeReference`, `ancestorStructure`, `descendantStructure`, `referencesDependencies`, `detectUnused`, `runMapping`, and `execute`.
2. **Modular Backend Service Layer**:
   - `MappingWorkspace`: Manages open documents (`TextDocumentService`), synchronization (`didOpen`, `didChange`, `didClose`), and AST caching.
   - `RmlEvaluator`: Coordinates node evaluation safely against sample iterations or custom context.
   - `RmlTracer`: Produces execution trace graphs with intermediate node value linkage.
   - `ReferenceCompleter`: Provides context-aware reference auto-completion across CSV, JSON, XML, databases, and Logical Views.
   - `StructureInspector`: Produces hierarchical AST descriptors with line/column coordinates.
3. **Automated TypeScript Contract Generation**:
   - `typescript-generator-maven-plugin` generates `rml-dev-server/src/generated/types.ts` on build from Java data transfer records.
4. **Frontend Stack**:
   - VS Code extension webviews use React 19 + TypeScript + Baukasten UI (`@baukasten-ui/core`, `@baukasten-ui/extra`).
   - No ad-hoc HTML strings or deprecated template helpers (`getWebviewHtml`).
5. **Static Analysis & Diagnostics Roadmap**:
   - Catch and integrate RML Error Reporting (RER) diagnostics into VS Code.
   - Integrate static validation for references, RML vocabulary compliance (e.g. unique field names in `rml-lv`), function signatures, and ontology conformance.

## Consequences
### Positive
- Strict compile-time and protocol type safety between Java backend and TypeScript extension.
- Clean separation of concerns with modular, independently testable services.
- Rich, reactive developer experience with live evaluation hints, trace graphs, and auto-completion.

### Negative
- Maven build must run code generation step prior to frontend compilation.
