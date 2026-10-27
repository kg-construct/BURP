# ADR-0012: Stateful Functions Handling in RML Dev Server

## Status
Declined

## Context
In Function Ontology (FnO) libraries—particularly external libraries like `idlab-functions-java`—certain functions are not functionally pure. Functions such as `implicitCreate`, `implicitUpdate`, `implicitDelete`, and `generateUniqueIRI` rely on mutable internal state (e.g., in-memory sets of previously seen identifiers, hash maps tracking updated values, and disk caches) to determine their outputs across successive record invocations.

In standard batch processor execution, input data is processed sequentially from record `0` to `N` in a single deterministic sweep. However, the RML Dev Server (`rml-dev-server`) provides interactive development features:
- Live expression evaluation at cursor positions (`rml/evaluate`).
- Single-node and intermediate transformation tracing (`rml/trace`).
- Arbitrary iteration slicing (e.g., evaluating records `5` through `8`, or repeatedly re-evaluating record `0` on every keystroke).

Running stateful functions under these interactive conditions causes severe issues:
1. **State Mutation Across Keystrokes**: Re-evaluating the same record twice yields different outputs (e.g., `implicitCreate` returns `true` on the first keystroke and `false` on subsequent evaluations).
2. **Disjoint Range Inconsistency**: Evaluating iterations `5-8` without having executed iterations `0-4` leads to incorrect state calculations because the prerequisite history is missing.
3. **Process-Level State Leaks**: Static state kept in shared JVM singletons pollutes subsequent unrelated evaluations.

## Decision Drivers
- **Idempotency**: Interactive evaluation in Dev Server must be repeatable and deterministic across keystrokes.
- **State Isolation**: Partial evaluations must never leak side effects into the global runtime environment.
- **Simplicity**: Avoid complex, ad-hoc state monkey-patching in external libraries.
- **Forward Compatibility**: Lay the groundwork for a comprehensive caching and state-replay architecture.

## Considered Options

### Option 1: Unrestricted Execution
Execute stateful functions as normal during all Dev Server evaluations.
- *Pros*: Requires no special checks or branching.
- *Cons*: Causes severe state pollution and non-deterministic results across editor keystrokes and non-zero iteration ranges.

### Option 2: Constrain Execution to Iterations Starting from Zero
Only execute stateful functions if the evaluation begins at the very first iteration (e.g., single iteration `0`, ranges `0..n`, or all data). Disallow execution when evaluating disjoint slices (e.g., `5..8`).
- *Pros*: Ensures stateful functions always observe history from the beginning.
- *Cons*: Still mutates process-level state on every evaluation; requires resetting global singletons between keystrokes; does not allow inspecting records in the middle of a dataset without paying the cost of evaluating all prior records.

### Option 3: Skip Execution in Interactive Dev Mode (Selected for Current Phase)
Do not execute stateful functions during interactive Dev Server evaluations (`rml/evaluate` and `rml/trace`). When an expression contains a stateful function, short-circuit evaluation and return an informative diagnostic notice explaining that stateful functions are skipped in interactive evaluation.
- *Pros*: Simplest and safest approach; completely eliminates state pollution and non-determinism with zero risk to process stability.
- *Cons*: Users cannot inspect live values of stateful functions in interactive cursor evaluations until the caching and replay system is available.

### Option 4: Encapsulated State Snapshots with Replay and Caching
Implement native versions of stateful functions where mutable state is fully encapsulated in the evaluation context rather than global static variables. The execution engine takes periodic state snapshots, allowing the engine to checkpoint state, replay from the nearest cached snapshot, and restore states when jumping across arbitrary slices (such as iterations `5..8`).
- *Pros*: Full support for interactive evaluation and slicing without state corruption.
- *Cons*: Requires a dedicated caching and snapshot infrastructure that does not yet exist in BURP.

## Decision

No stateful function supported by BURP, waiting for better stateful abstraction specification in the FNML specs.