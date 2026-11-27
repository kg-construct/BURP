# ADR-0006: Declarative RDF Object Loader & @RdfMappedFrom

## Status
Accepted

## Context
Originally, parsing RML mapping graphs into domain model instances required procedural post-processors (`populateExpression`, custom if-else ladders) and scattered manual type dispatching. When adding new expression types or language features, developers had to manually update procedural mapping code in multiple locations.

## Decision Drivers
- **Declarative Simplicity**: Mapping rules should be co-located with domain model definitions via annotations.
- **Polymorphism**: Properties like `rml:reference`, `rml:template`, and `fno:Execution` on `ExpressionMap` should be resolved polymorphically.
- **Provenance**: Line and column origins (`Origin`, `StatementParts`) must be injected automatically without manual wiring.

## Considered Options
1. **Procedural Post-Processing (`populateExpression`)**:
   - *Pros*: Simple procedural Java.
   - *Cons*: High boilerplate, easy to forget cases, tight coupling to loader implementation.
2. **Declarative Annotation Scanner (`@RdfMappedFrom`, `@OriginOf`, `@RdfShortcutProperty`)**:
   - *Pros*: Clean domain model, reflection-driven polymorphic candidate resolution, automatic origin injection, extensible.
   - *Cons*: Requires reflection metadata caching.

## Decision
Adopt `ReflectiveRdfObjectLoader` with `@RdfMappedFrom` and `@OriginOf`. Polymorphic expression properties declare their candidate classes directly on the field (e.g. `@RdfMappedFrom({Template.class, RawReference.class, RDFNodeConstant.class, FunctionExecution.class})`), eliminating procedural post-processors.

## Consequences
### Positive
- `populateExpression` and ad-hoc post-processors eliminated.
- Domain classes explicitly declare their valid RDF manifestations.
- Origin statements and provenance pointers are populated automatically.

### Negative
- Reflective loader requires careful handling of constructor invocation and custom mappers.
