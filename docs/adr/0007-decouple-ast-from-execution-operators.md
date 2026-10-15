# ADR-0007: Decouple AST Specification from Execution Operators

## Status
Proposed

## Context
In the current BURP model, classes under `burp.model` (like `TriplesMap`, `SubjectMap`, `LogicalView`, `ExpressionField`) combine declarative AST representation (`@RdfProperty`, provenance pointers) with runtime evaluation logic (`generateTerms()`, `iterator()`, `enrich()`, cached `iterations`). This causes state pollution during execution and prevents alternative evaluation strategies (e.g. streaming pipelines, database pushdown, or LSP tooling).

## Decision Drivers
- **Multi-Tenant / Multi-Threaded Execution**: AST instances should be immutable and reusable across concurrent runs with different input data.
- **Engine Pluggability**: Support specialized evaluators (Dev Server / LSP interactive step evaluation, high-throughput streaming batch, DuckDB/SQL pushdown).
- **Optimization Opportunities**: Enable query planning, filter pushdown, and join reordering before execution.

## Decision
Separate the codebase into three distinct tiers:
1. **Declarative AST (Specification)**: Pure, immutable POJOs/records representing the RML graph.
2. **Static Analysis & Plan Compiler**: Static capability analyzer, scope resolver, and operator DAG builder.
3. **Pluggable Operator Engine (`RmlEvaluator`)**: Dedicated execution operators (`SourceScanOp`, `JoinOp`, `TermGenOp`, `SinkEmitOp`) implementing distinct execution strategies (`TraceableDevEvaluator`, `StreamingBatchEvaluator`, `DuckDbPushdownEvaluator`).

## Consequences
### Positive
- Zero runtime state mutations on AST nodes.
- High-throughput batch mode without tracing overhead.
- Interactive Dev Server can evaluate single nodes without executing entire plans.

### Negative
- Requires a compilation/planning step between parsing and execution.
