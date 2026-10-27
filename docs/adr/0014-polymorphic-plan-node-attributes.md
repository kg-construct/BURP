# ADR-0014: Polymorphic PlanNode Attributes and Cross-Tree References Architecture

## Status
Accepted

## Context

Execution plan nodes (`PlanNode`) are inspected and visualized in the VS Code extension across two primary views:
1. **Plan Node Hierarchy (Structure View)**: Displays the full AST execution plan tree with node identifiers, types, resources, and configuration parameters.
2. **Trace Graph Viewer & Cards**: Displays runtime execution values, lineages, and dependencies between mapping components.

Previously, node attribute inspection suffered from several limitations:
- **Monolithic switch**: `PlanNodeDescriptor.getParametricValues()` used a procedural `switch` statement that violated the Open-Closed Principle and required manual updates whenever new node types were introduced.
- **Lack of Source Provenance**: Attributes and parameters lacked Turtle source location information (`Origin` / `Range`), preventing users from clicking an individual attribute (e.g. `template`, `reference`, `termType`) to jump directly to its declaration in the mapping file.
- **No Inferred/Default Distinction**: Implicit or inferred attributes (e.g., default `termType: IRI` or `BlankNode`) could not be visually distinguished from explicitly declared statements.
- **Cross-tree References conflation**: References across independent subtrees (such as `ReferencingObjectMap.parentTriplesMap` and `ViewJoin.parentLogicalView`) could not be placed in `children()` because doing so caused tree cycles and corrupted parent pointers during AST tree wiring (`PlanWiring.wireParentChildren`). Flat string parameters lost the relation to the target node's identity and navigation capabilities.

## Decision

We adopt the **Polymorphic Accumulator / Sink Pattern** for node attributes, combined with a first-class **Cross-Tree Reference Model** for relational links.

### 1. Attribute Accumulator Pattern (`collectAttributes`)
Rather than allocating intermediate maps or using reflection/JSON serialization (which risks cyclic recursion and lacks compile-time safety), `PlanNode` defines a default accumulator hook:

```java
@FunctionalInterface
public interface AttributeCollector {
    void put(String key, String value, @Nullable Origin origin, boolean isDefault);
}
```

```java
public interface PlanNode {
    default void collectAttributes(AttributeCollector sink) {}

    default List<PlanNodeAttribute> nodeAttributes() {
        List<PlanNodeAttribute> list = new ArrayList<>();
        collectAttributes((k, v, o, isDef) -> list.add(new PlanNodeAttribute(k, v, o, isDef)));
        return list.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    default Map<String, String> attributeValues() {
        Map<String, String> map = new LinkedHashMap<>();
        collectAttributes((k, v, o, isDef) -> map.put(k, v));
        return map.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }
}
```

Each concrete `PlanNode` overrides `collectAttributes` to contribute its domain attributes with provenance:
- `Template`: contributes `"template"` with `emitOrigin()`
- `RDFNodeConstant`: contributes `"constant"` with `originOf("constant")`
- `Reference`: contributes `"reference"` with `originOf("reference")`
- `TermMap`: delegates to `super.collectAttributes(sink)` (`ExpressionMap`), then contributes `"termType"` (marked `isDefault = true` when inferred)
- `AbstractLogicalSource` / `FileBasedLogicalSource`: contributes `"source"` and `"iterator"`
- `JoinCondition`: contributes `"child"` and `"parent"`
- `ViewJoin`: contributes `"joinType"`

### 2. Cross-Tree References (`nodeReferences` vs `children`)
Cross-tree relational links are modeled separately from tree ownership:
- **`children()`**: Represents strict AST tree ownership. Never contains cross-tree targets like `parentTriplesMap` or `parentLogicalView`.
- **`nodeReferences()`**: Returns structured relational links:
  ```java
  public record PlanNodeReference(String role, PlanNode target, @Nullable Origin origin) {}
  ```
- **`references()`**: Returns `Iterable<PlanNode>` targets for reachability analysis.
- **`descendants(Class<T>)`**: Strictly traverses owned `children()`.
- **`descendantsAndReferences(Class<T>)`**: Traverses both `children()` and `references()`, ensuring referenced entities (such as logical views joined in `ViewJoin`) receive CodeLenses and workspace indexing.

Concrete reference implementations:
- `ReferencingObjectMap`: exposes `parentTriplesMap` under role `"parentTriplesMap"` with `originOf("parentTriplesMap")`.
- `ViewJoin`: exposes `parentLogicalView` under role `"parentLogicalView"` with `originOf("parentLogicalView")`.

### 3. Protocol DTOs and DevServer Models
`PlanNodeItem` replaces flat `Map<String, String> parameters` with:
- `List<PlanAttributeItem> attributes`: each attribute has `key`, `value`, `originRange`, and `isDefault`.
- `List<PlanReferenceItem> references`: each reference has `role`, `targetId`, `targetLabel`, `targetResource`, and `originRange`.

`TraceNode` includes `List<PlanAttributeItem> attributes` to display defining attributes on execution trace cards, while omitting cross-tree references (as dataflow dependencies are tracked via `inputs`, `outputs`, and `dependencyIds`).

### 4. Client Webview Interaction (PlanApp & TraceNodeDetailsCard)
- **Attribute Badges**:
  - Explicit attributes render as clickable pills. Clicking jumps directly to the declaring range in Turtle code.
  - Default/inferred attributes (`isDefault: true`) render with dashed borders and italic styling, with tooltip indicating inferred status and no range jump.
- **Reference Chips**:
  - Render as `parentTriplesMap ➔ ex:ParentMapping [⇲]`.
  - Clicking the label jumps to the referencing statement in Turtle.
  - Clicking `[⇲]` scrolls to and highlights the target node within the Plan Hierarchy tree (`onNavigateToNode(ref.targetId)`).
- **Auto-Refresh on Expand**:
  - When the webview view expands or becomes visible in VS Code, `StructureViewProvider` auto-syncs the active mapping editor and refreshes the plan.
  - `PlanApp` triggers `{ command: 'refresh' }` on initial mount to guarantee instantaneous display without requiring manual clicks.

## Consequences

### Positive
- **Zero code duplication**: Child classes (`TermMap`) inherit and compose parent attributes (`ExpressionMap`) naturally via standard OOP virtual dispatch.
- **No monolithic switches**: Eliminates procedural type checks and enables open extension across modules.
- **Rich source navigation**: Fine-grained provenance links every attribute and reference to its exact Turtle source tokens.
- **Tree integrity**: Avoids cycles and parent pointer corruption by keeping cross-tree references separate from AST `children()`.
- **Responsive UX**: Instant automatic loading when expanding the VS Code structure panel.

### Negative / Trade-offs
- Core `PlanNode` interface gained `collectAttributes`, `nodeAttributes`, and `nodeReferences` methods.
- Tests and serializers must distinguish between tree descendants and cross-tree reference links.
