# ADR-0003: Polymorphic PlanNode Evaluation Architecture

## Status
Accepted

## Context
Originally, `RmlEvaluator.java` contained a central procedural `switch (node)` / `if (node instanceof ...)` dispatcher to evaluate AST nodes (`TriplesMap`, `TermGenerator`, `ExpressionMap`, `Template`, `Reference`, `FunctionExecution`, `PredicateObjectMap`, `RDFNodeConstant`, `GatherMap`, `JoinCondition`).

This procedural approach suffered from several limitations:
- **Violation of Open-Closed Principle**: Adding new AST node types or custom expressions required modifying central evaluator dispatching logic.
- **Scattered Evaluation Logic**: Evaluation rules were detached from the domain models defining them.
- **Inflexible Subclass Overrides**: Subclasses like `TermMap` (extending `ExpressionMap` while implementing `TermGenerator`) could inherit unintended raw evaluation behavior from parent classes instead of generating typed RDF terms.

## Decision Drivers
- **Polymorphism & Cohesion**: Evaluation logic for a plan node should reside within the node class itself.
- **Simplicity & Safety**: Central evaluator services (`RmlEvaluator`) should act as thin, exception-safe coordinators rather than procedural dispatchers.
- **Extensibility**: New `PlanNode` implementations can define their evaluation behavior independently.

## Considered Options
1. **Centralized Procedural Dispatcher (`switch (node)`) in `RmlEvaluator`**:
   - *Pros*: All evaluation code is in one file.
   - *Cons*: High coupling, fragile type-checks, hard to maintain as language features grow.
2. **Polymorphic `evaluateNode(Iteration)` on `PlanNode`**:
   - *Pros*: Object-oriented, encapsulated, clean inheritance and overriding, trivial error boundary in `RmlEvaluator.evaluateNodeSafe`.
   - *Cons*: Adds a method to the `PlanNode` interface.

## Decision
Declare a default method on `PlanNode`:
```java
default List<RmlEvaluationResult> evaluateNode(Iteration iter) {
    return Collections.emptyList();
}
```
Implement `evaluateNode(Iteration iter)` directly on each domain class:
- `TriplesMap`: Generates statements (`generate(iter)`).
- `TermGenerator`: Generates typed RDF terms (`generateTerms(iter)`).
- `ExpressionMap`: Generates raw expressions and converts values to `LiteralTerm` unless `this instanceof TermGenerator`.
- `Template`: Delegates to parent `TermGenerator.generateTerms(iter)` when enclosed in a term generator, or computes escaped template values.
- `Template.ReferenceSegment`: Evaluates individual reference values (`reference().getStrings(iter)`).
- `Reference`: Evaluates reference values (`getStrings(iter)`).
- `FunctionExecution`: Evaluates function output terms (`values(iter)`).
- `PredicateObjectMap`, `RDFNodeConstant`, `GatherMap`, `JoinCondition`: Implement their corresponding evaluation strategies.

Simplify `RmlEvaluator.evaluateNodeSafe(node, iter)` to wrap `node.evaluateNode(iter)` with `BurpException` diagnostic reporting.

## Consequences
### Positive
- Evaluation logic is cleanly encapsulated within the domain model.
- Central evaluator is streamlined and robust.
- Subclasses properly override or refine evaluation semantics (e.g. `TermMap` calling `generateTerms` rather than raw `ExpressionMap.generateValues`).

### Negative
- Model classes take on execution/evaluation concerns, but default methods maintain backward compatibility.
