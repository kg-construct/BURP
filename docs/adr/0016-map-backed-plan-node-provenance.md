# ADR-0016: Map-Backed Plan Node Provenance & Cursor Selection

## Status
Accepted

## Context
In BURP's language server (`rml-dev-server`) and execution engine, fine-grained source provenance is essential for two distinct purposes:
1. **Diagnostic & Execution Reporting**: Emitting precise `RmlExecutionReport` errors (e.g., `ReferenceFormulationSyntaxError`, `IncorrectTermType`) highlighting the exact offending property or token.
2. **Language Intelligence & Editor Interaction**: Finding the active AST `PlanNode` under the editor cursor (`findNodeBySelector`), computing syntax ranges, rendering inlay hint decorations, and inspecting sub-trees.

Previously, provenance tracking suffered from several architectural defects:
- **Scattered `@OriginOf` Fields**: Individual AST classes manually declared fields like `@OriginOf(propertyName = "expression") public Origin expressionOrigin;`. Many properties were unannotated, leading to empty ranges for entire node classes (such as `GatherMap` or `FunctionExecution`).
- **Defective Subclass `getSourcePointers()`**: Overrides manually extracted pointers with erroneous assumptions. For example, `PredicateObjectMap.getSourcePointers()` extracted the subject of parent statements (`rr:predicateObjectMap`), resolving to the parent `TriplesMap` and inflating the POM's range over the entire block.
- **Missing Container & Token Origins**: Blank node markers `[ ... ]`, list wrappers `( ... )`, and `rdf:type` statements (e.g. `a rml:IterableField`) were not associated with the resulting AST nodes, meaning child nodes often had narrower or empty ranges compared to their parent containers.
- **Cursor Selection Ambiguity**: Cursors placed on child constructs (`ObjectMap`, `GatherMap`, `FunctionExecution`, or inner references) incorrectly resolved to the parent `PredicateObjectMap`.
- **Maintenance Burden**: Adding a property to an AST node required declaring redundant boilerplate origin fields, reflection metadata, and custom pointer aggregation.

## Decision Drivers
- **Zero Boilerplate for AST Nodes**: AST nodes must not require repetitive `@OriginOf` declarations for every property.
- **Automatic Provenance Ingestion**: The loader must automatically capture origins for all mapped properties, lists, shortcuts, `rdf:type` statements, and parent-child connecting statements.
- **O(1) Uniform Provenance Access**: Any `PlanNode` must be able to return all its source pointers without class-specific reflection scanning at runtime.
- **Deepest-Node Cursor Selection**: Cursor-to-node matching must reliably pick the deepest AST node enclosing the cursor position.
- **Multi-File Readiness**: Provenance must capture the source file/URI, anticipating multi-file mappings and modular source graphs.
- **Compile-Time Safety & Error Ergonomics**: Error emitters must easily obtain detached, fully qualified `Origin` records with file path, offending AST node, and statement pointers.

## Considered Options

### Option 1: Explicit Typed Origin Fields (`@OriginOf`) on All Plan Nodes
- *Pros*: Explicit compile-time fields on AST classes (e.g., `node.iteratorOrigin`).
- *Cons*: Massive boilerplate; every new field requires manual origin declarations; easily forgotten by developers; gathering all node origins requires reflection scanning of declared fields at runtime; breaks down for dynamic properties and extra statements (`rdf:type`, connecting pointers).

### Option 2: Pure Dynamic Code Generation (APT / Bytecode Weaving)
- *Pros*: Generated accessors without manual typing.
- *Cons*: Java does not support extension traits or class augmentation without complex bytecode manipulation; increases build complexity; breaks IDE navigation and autocompletion; difficult to debug.

### Option 3: Map-Backed Provenance in `AbstractPlanNode` (Chosen)
- *Pros*:
  - Centralized in `AbstractPlanNode`: `Map<String, Origin> propertyOrigins` and `List<Origin> extraOrigins`.
  - `ReflectiveRdfObjectLoader` automatically injects property statements during mapping without requiring annotations on every field.
  - Aggregating all pointers for a node is a simple map collection traversal: `getAllOrigins()`.
  - `AbstractPlanNode.getSourcePointers()` and `nodeRanges()` provide complete, correct defaults for all subclasses, eliminating broken subclass overrides.
  - Specific convenience accessors (`node.originOf("fieldName")` or typed getters `template.templateOrigin()`) remain simple and optional.
  - Connecting statements (`parent -> prop -> child`) and `rdf:type` statements are stored in `extraOrigins`, giving child nodes full text span coverage.
- *Cons*: Property names are referenced by String keys unless wrapped in convenience getters.

## Decision

### 1. Unified Provenance Storage in `AbstractPlanNode`
`AbstractPlanNode` maintains:
```java
public abstract class AbstractPlanNode implements PlanNode {
    protected final Map<String, Origin> propertyOrigins = new LinkedHashMap<>();
    protected final List<Origin> extraOrigins = new ArrayList<>();

    public @Nullable Origin originOf(String propertyName) {
        return propertyOrigins.get(propertyName);
    }

    public void putOrigin(String propertyName, Origin origin) {
        propertyOrigins.put(propertyName, origin);
    }

    public void addExtraOrigin(Origin origin) {
        extraOrigins.add(origin);
    }

    public Collection<Origin> getAllOrigins() {
        if (extraOrigins.isEmpty()) return propertyOrigins.values();
        List<Origin> all = new ArrayList<>(propertyOrigins.size() + extraOrigins.size());
        all.addAll(propertyOrigins.values());
        all.addAll(extraOrigins);
        return all;
    }

    @Override
    public List<RDFPointer> getSourcePointers() {
        return getAllOrigins().stream()
                .filter(Objects::nonNull)
                .map(Origin::sourceStatements)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }

    @Override
    public List<PointRange> nodeRanges() {
        return ProvTurtleVisitor.retrieveTurtleLocation(getSourcePointers());
    }

    public Origin emitOrigin(String propertyName) {
        Origin o = originOf(propertyName);
        if (o == null) return new Origin(this, null, getSourceFile());
        return new Origin(this, o.sourceStatements(), o.file() != null ? o.file() : getSourceFile());
    }
}
```

### 2. Automatic Injection in `ReflectiveRdfObjectLoader`
`ReflectiveRdfObjectLoader` populates provenance automatically:
- **Properties (`@RdfProperty`, `@RdfShortcutProperty`, `@RdfList`, `@RdfMappedFrom`)**:
  When a property statement `stmt` is processed for an `AbstractPlanNode`, it records:
  ```java
  planNode.putOrigin(fieldName, new Origin(null, Collections.singletonList(StatementParts.from(stmt, StatementPart.Object)), file));
  ```
- **RDF Lists (`@RdfList`)**:
  The head statement `resource.listProperties(prop).getFirst()` is recorded so list properties (`gather`, etc.) receive valid origins.
- **Identity & Connecting Statements (`extraOrigins`)**:
  - `rdf:type` statements (`resource.listProperties(RDF.type)`) are added as `extraOrigins` (`StatementPart.Predicate, StatementPart.Object`).
  - When mapping child nodes via `mapNodeValue(..., stmt)`, the incoming statement is attached to the child node as an `extraOrigin`:
    ```java
    if (child instanceof AbstractPlanNode childNode && stmt != null) {
        childNode.addExtraOrigin(new Origin(null, Collections.singletonList(StatementParts.from(stmt, StatementPart.Object)), file));
    }
    ```

### 3. Removal of Broken Subclass Overrides
- Remove manual `getSourcePointers()` from `PredicateObjectMap.java`, `TermMap.java`, and `GatherMap.java`.
- Subclasses inherit the complete, correct base implementation from `AbstractPlanNode`.

### 4. Depth-Based Cursor Selection in `MappingWorkspace`
`MappingWorkspace.findNodeBySelector` matches all nodes whose `nodeRanges()` contain the cursor position and selects the candidate with the maximum tree depth (`node.ancestor(...)` depth). When child nodes have accurate text spans, depth-based selection deterministically selects the innermost node (e.g. inner `ObjectMap` > `GatherMap` > outer `ObjectMap` > `PredicateObjectMap`).

### 5. Visual Text Span Debug Highlighting
In `rml-dev-server`, a `TextEditorDecorationType` with background color `rgba(255, 215, 0, 0.15)` is rendered over the selected node's `originRange`, giving immediate visual confirmation of the node's exact source span in VS Code, when the setting is enabled `rml.nodeBackground`.

For debgging purposes, a setting `rml.nodeBackgroundAll` is enabled all nodes have a background colors generatde rendomly so that each node has a different color to check visually that we capture corectly everithing. (that steting is diabled by default and primarely for debugging).
## Consequences

### Positive
- **Zero Boilerplate**: No need to annotate every field with `@OriginOf`.
- **Complete AST Provenance**: All nodes—including `GatherMap`, `FunctionExecution`, and `Input`—have accurate source pointers and ranges.
- **Precise Cursor Mapping**: Cursor selection on inner maps, functions, and list items resolves to the exact child node instead of the enclosing `PredicateObjectMap`.
- **Multi-File Readiness**: Every `Origin` carries the source file path from the loader.
- **Clean Subclass Architecture**: Subclasses no longer override `getSourcePointers()` with error-prone custom logic.

### Negative
- Origin lookups by property name (`node.originOf("propertyName")`) use String keys unless wrapped in convenience getters.
- Memory: AST nodes retain an internal map of property origins (negligible for typical mapping files containing dozens to hundreds of triples maps).
