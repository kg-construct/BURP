# ADR-0008: First-Class rml:condition Modeling in ExpressionMap

## Status
Accepted

## Context
Previously, `rml:condition` was handled via an in-memory SPARQL graph rewrite pre-processor (`normalizeConditionShortcuts`). This rewrite mutated the Jena mapping model prior to parsing by transforming `rml:condition` into a synthetic `idlab-fn:IF` function execution.

This approach had multiple architectural shortcomings:
1. **Incomplete Coverage**: It ignored conditions attached to collection maps (`rml:gather`) see test case `BURP-FNML+CC-*`.
2. **Broken Composability & ASTs**: Combining `rml:condition` with `rml:functionExecution` generated synthetic, nested ASTs that broke reference resolution and execution.
3. **Loss of Provenance**: Graph mutations did not preserve the original RDF source statements, degrading the accuracy of source locations in Dev Server tooling.

## Decision Drivers
- **Composability**: `rml:condition` must work uniformly across all expression types (constants, references, templates, function executions) and term structures (e.g. `rml:gather`).
- **AST Fidelity & Provenance**: The domain model must represent the exact source mapping without destructive graph mutations, preserving source ranges for LSP tooling.
- **Declarative Consistency**: Leverage BURP's declarative `@RdfProperty` and `@OriginOf` loader architecture.

## Considered Options
1. **SPARQL Update Pre-processing (`normalizeConditionShortcuts`)**:
   - *Pros*: Reused existing `idlab-fn:IF` function execution implementation, closer to specs of RML-FNML.
   - *Cons*: Mutated input graph, broke FnML and `rml:gather` interactions, destroyed source provenance.
2. **First-Class Domain Model Representation on `ExpressionMap`**:
   - *Pros*: Declaratively loaded, preserves untouched mapping graphs, seamlessly handles `rml:gather` and function executions, preserves full source provenance.
   - *Cons*: Requires condition evaluation check during value/term generation.

## Decision
Represent `rml:condition` as a first-class field on `ExpressionMap`:
- Added annotations for the loader, removed the sparql query.
- Include condition in children of ExpressionMap.
- Evaluate condition truthiness and short-circuit directly in `ExpressionMap.generateValues` and `TermMap.generateTerms`.

## Consequences
### Positive
- Eliminates graph mutation and SPARQL preprocessing.
- Enables `rml:condition` inside or alongside `rml:gather` collections.
- Preserves accurate source provenance and syntax ranges for IDE/Dev Server tooling.

### Negative
- Expression and term generation methods must explicitly evaluate `isConditionSatisfied(...)`.
