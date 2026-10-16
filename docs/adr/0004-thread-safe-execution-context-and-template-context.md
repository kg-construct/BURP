# ADR-0004: Thread-Safe Evaluation via Context Hierarchy and Static State Elimination

## Status
Accepted

## Context
Originally, BURP relied on global static variables (`Main.report`, `Main.baseIRI`, `Main.mappingFile`, and static blank node sequence maps in `TermMap`) to accumulate execution diagnostics, resolve base IRIs, and generate unique blank node IDs.

With the introduction of the RML Dev Server and concurrent LSP/RPC evaluation requests (evaluating multiple files, ranges, or different mappings simultaneously in background worker threads), this static state caused severe race conditions, cross-thread diagnostic leakage, and base IRI collisions. Additionally, `Template` evaluation lacked compile-time guarantees that reference escaping rules (`SAFE_IRI`, `SAFE_URI`, `UNSAFE`) were explicitly supplied by parent term maps.

## Decision Drivers
- **Thread Safety & Concurrency**: Multiple evaluation requests must execute concurrently across threads with zero static state collision.
- **Type-Safe Template Safety**: Java's type system should guarantee at compile time that template reference safety is explicitly specified when evaluating templates.
- **Cancellation & Isolation**: Long-running evaluations should support cancellation tokens, scoped error reports, and thread-isolated blank node ID allocation without global locks.
- **Backward Compatibility**: Keep default 1-argument method overloads across the AST for testing and lightweight invocation.

## Considered Options
1. **ThreadLocal Storage for Global State**:
   - *Pros*: Minimal changes to method signatures.
   - *Cons*: Hidden dependencies, memory leak risks in thread pools, hard to branch contexts (e.g. child template safety or base IRI overrides).
2. **Explicit Parameterized Execution Context Hierarchy (`EvaluationContext` & `TemplateContext`)**:
   - *Pros*: Explicit dependency injection, compile-time safety via sealed interfaces, clean immutable branching (`withTemplateSafety`, `withBaseIri`), eliminates all static fields on `Main` and AST nodes.
   - *Cons*: Requires threading context parameters through AST evaluation methods.

## Decision
Adopt a hierarchical sealed context system:
- **`EvaluationContext`**: Sealed base interface carrying `RmlExecutionReport`, `baseIri`, `BlankNodeScope`, `CancellationToken`, and active target scopes.
- **`TemplateContext extends EvaluationContext`**: Sub-interface requiring explicit `TemplateReferenceSafety` (`SAFE_IRI`, `SAFE_URI`, `UNSAFE`).
- `Template.values(Iteration, TemplateContext)` enforces at compile time that reference escaping is defined. `TermMap` acts as the bridge that sets the safety mode based on its term type (`IRI` -> `SAFE_IRI`, `URI` -> `SAFE_URI`, `Literal`/`BlankNode` -> `UNSAFE`).
- Remove all static fields (`Main.report`, `Main.baseIRI`, `Main.mappingFile`, static blank node counters). Scope reports and base IRIs directly inside `EvaluationContext`.
- Move term generator methods (`generateIRIs`, `generateURIs`, `generateBlankNodes`, `generateLiterals`) from `ExpressionMap` to `TermMap`.

## Consequences
### Positive
- Full thread-safety: any number of mapping evaluations and Dev Server requests can run concurrently in parallel threads.
- Compile-time safety: impossible to evaluate `Template` without specifying reference safety.
- Complete elimination of global mutable state in `Main`, `Parse`, `FunctionsRegistry`, and AST nodes.
- Built-in cancellation support via `CancellationToken`.

### Negative
- Evaluation signatures across `PlanNode`, `TermGenerator`, `ExpressionMap`, `TermMap`, `GatherMap`, and `FunctionExecution` require `EvaluationContext` parameters (mitigated by backward-compatible default overloads).
