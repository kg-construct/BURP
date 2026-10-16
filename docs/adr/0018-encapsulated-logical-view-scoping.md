# ADR-0018: Encapsulated Logical View Scoping and AST Parent Unification

## Status

Accepted

## Context

In BURP, Language Server services (`EvaluationContexts`, `ReferenceSchemaResolver`, `SampledDataSchemaProvider`) inspect and evaluate mapping nodes interactively. Previously:
1. **Leaked RML-LV Internals**: Services directly imported `burp.model.lv.IterableField` and performed ad-hoc `getParent()` walks to detect reference formulations and extract sub-iterations. This logic was incomplete, skipping intermediate `ExpressionField`s that parse parent string payloads into sub-iterations (e.g., CSV column containing JSON, as in `RMLLVTC0007a`).
2. **Duplicate Parent Hierarchy**: `Field` maintained a parallel parent hierarchy via a custom `FieldParent` interface and `Field.wire()` alongside the standard `PlanNode.getParent()` wired by `PlanWiring`. This led to divergence and AST corruption (e.g., `ViewJoin.children()` duplicating `fieldExpressionMap` and overwriting its parent).
3. **Divergent Evaluation Engines**: Full mapping execution (`LogicalView.iterator()`) used `Field.expand()` / `enrich()`, whereas interactive DevServer evaluation re-implemented iteration traversal independently.

## Decision Drivers

- **Information Hiding**: DevServer completion, validation, and evaluation services must be decoupled from concrete Logical View AST implementations.
- **Single Source of Truth for AST Hierarchy**: Rely solely on standard `PlanNode.getParent()` and eliminate custom wiring mechanisms.
- **Format-Agnostic Composition**: Transparently support nested reference formulation switches (e.g. CSV $\to$ JSON, RDB $\to$ JSON) across full execution, interactive evaluation, autocomplete, and diagnostics.

## Decision

1. **Encapsulate Scoping in `LocalReferenceScope`**:
   - `LogicalView`, `ExpressionField`, and `IterableField` implement `LocalReferenceScope`.
   - `LocalReferenceScope.getReferenceFormulation()` resolves the effective formulation dynamically (an `IterableField` declaring `rml:referenceFormulation` overrides its parent scope, while `ExpressionField` inherits it).
   - `LocalReferenceScope.scopeIterations(List<Iteration>)` transforms base source iterations into the local iteration type (e.g., parsing raw column payloads into `JSONIteration`s via `LogicalSourceFactory.changeIterator`).
2. **Eliminate `FieldParent`**:
   - Remove `FieldParent`, `Field.parentField`, and `Field.wire(...)`. All parent lookups use `PlanNode.getParent()` and `ancestor(Class<T>)`.
   - Fix `ViewJoin.children()` to avoid adding `fieldExpressionMap` twice.
3. **Unify Services via Scope Protocol**:
   - Services (`EvaluationContexts`, `ReferenceSchemaResolver`, `SampledDataSchemaProvider`) query `targetNode.ancestor(LocalReferenceScope.class)` exclusively, with zero imports or checks for `IterableField`.

## Consequences

### Positive
- **Complete Decoupling**: DevServer services have zero dependencies on RML-LV internal field types.
- **Robust Format Transitions**: Multi-level nesting (CSV $\to$ JSON, RDB $\to$ JSON, JSON $\to$ XML) works uniformly across evaluation, autocomplete, and syntax validation.
- **Simpler AST**: One canonical tree maintained by `PlanWiring`, eliminating parent desynchronization bugs.

### Negative
- Nested interactive evaluation requires evaluating enclosing `ExpressionField`s to produce intermediate string payloads before sub-iterating.
