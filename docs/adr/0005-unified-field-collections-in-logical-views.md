# ADR-0005: Unified Field Collections in Logical Views

## Status
Accepted

## Context
Previously, `Field.java`, `LogicalView.java`, and `ViewJoin.java` maintained three parallel collections:
- `@RdfProperty(Rml.field) public List<Field> fields;`
- `public List<ExpressionField> expressionFields;`
- `public List<IterableField> iterableFields;`

This dual representation led to synchronization bugs (e.g. nested fields being added to `fields` but absent from `expressionFields`, causing empty field evaluation during `Field.expand()`).

## Decision Drivers
- **Single Source of Truth**: All child fields should be stored in one canonical collection.
- **Bug Prevention**: Avoid state divergence between raw fields and typed sub-lists.

## Decision
Maintain only `@RdfProperty(Rml.field) public List<Field> fields`. Typed sub-views are derived dynamically on demand via `getExpressionFields()` and `getIterableFields()`.

## Consequences
### Positive
- Eliminated state sync bugs across nested views and joins.
- Reduced boilerplate in domain classes.

### Negative
- Slight streaming/filtering overhead when accessing typed sub-views (negligible for typical mapping sizes).
