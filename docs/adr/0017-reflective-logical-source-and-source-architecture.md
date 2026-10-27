# ADR-0017: Declarative Loading for Logical Sources and Physical Sources

## Status
Proposed

## Context
In BURP, `LogicalSource` is the only remaining major AST component that bypasses `ReflectiveRdfObjectLoader`. Instead, `RmlLoader` intercepts `AbstractLogicalSource` via a procedural custom mapper that delegates to `LogicalSourceFactory` and an SPI (`LogicalSourceProvider`), with implementations like `JSONSourceProvider`, `CSVSourceProvider`, `XMLSourceProvider`, etc.

This legacy architecture causes several critical design and operational issues:
1. **Provenance Black Hole**: Statements under a `LogicalSource` are not tracked by the reflective loader. To prevent unused-statement false alarms, `RmlLoader` uses a recursive traversal hack (`recordLogicalSourceStatements`). Furthermore, `AbstractLogicalSource.getSourcePointers()` contains an explicit TODO:
   ```java
   // TODO: Needs fixing this hack with OriginOf while loading when rationalising LogicalSource and Source loading.
   ```
2. **Conflation of Physical Source and Logical Iteration**:
   In the RML specification, a physical **`Source`** (`rml:source`—such as a local file, remote HTTP URL, database connection, or SPARQL endpoint) is independent of the **`LogicalSource`** (`rml:referenceFormulation` and `rml:iterator`—such as JSONPath, CSV, or XPath).
   BURP's current class hierarchy conflates these:
   `JSONSourceRFC extends FileBasedLogicalSource extends LogicalSource`
   Here, `FileBasedLogicalSource` handles file path resolution, decompressive streaming, charset encoding, and null values, while `JSONSourceRFC` handles JSONPath queries. This inheritance prevents reusing JSONPath iteration over non-file sources (e.g., HTTP streams, database JSON columns, or in-memory data).
3. **Procedural Boilerplate**: Each `LogicalSourceProvider` manually parses RDF properties from Jena `Resource` objects (e.g. `FileBaseSourceProvider.getFile(...)`, `ls.getProperty(RML.iterator)`), manually constructs domain instances, and manually tracks statement parts.

## Decision Drivers
- **Declarative Consistency**: `LogicalSource` and `Source` must be loaded by `ReflectiveRdfObjectLoader` using standard annotations (`@RdfProperty`, `@RdfMappedFrom`, `@RdfPolymorphic`, `@RdfType`), matching the rest of BURP.
- **Separation of Concerns**: Decouple physical data acquisition (`Source`) from logical formulation and iteration (`LogicalSource`).
- **Complete Provenance**: All attributes of both `LogicalSource` (e.g., `iterator`, `referenceFormulation`) and `Source` (e.g., `path`, `encoding`, `compression`, `nullValue`) must automatically populate the map-backed `propertyOrigins` store ([ADR-0016](file:///home/jakub/Documents/Dev/BURP-adr/0016-map-backed-plan-node-provenance.md)).
- **AST Hierarchy & Cursor Selection**: `Source` should be a child `PlanNode` of `LogicalSource`, enabling independent cursor selection, range computation, and syntax diagnostics.
- **Pluggable Extensibility via Java SPI**: Third parties and modules must be able to extend BURP with new logical source types via Java `ServiceLoader` without modifying core loader classes.
- **Source Compatibility Validation**: Detect unsupported pairings of logical source formulations and physical source types, raising standard `RER.UnsupportedMapping` diagnostics.
- **Zero Legacy Compatibility Clutter**: Do not introduce backward-compatibility adapters or keep dead procedural factories.

## Considered Options

### Option 1: Keep `LogicalSourceProvider` Factories and Manually Inject `propertyOrigins`
- *Pros*: Minimal change to class hierarchy.
- *Cons*: Keeps procedural parsing boilerplate; retains the workaround `recordLogicalSourceStatements`; keeps `FileBasedLogicalSource` inheritance coupling; fails to bring `Source` into the AST DAG.

### Option 2: Full Decoupling and Declarative Reflection Loading (Chosen)
- *Pros*:
  - Completely aligns `LogicalSource` and `Source` with `ReflectiveRdfObjectLoader`.
  - Clean separation: `LogicalSource` owns the iteration and reference formulation; `Source` owns physical data streams.
  - Automatic provenance for both nodes without manual origin bookkeeping.
  - `Source` becomes a first-class AST child node.
  - Open for extension via `LogicalSourceTypeProvider` SPI.
- *Cons*: Requires migrating existing `LogicalSource` subclasses (`JSONSourceRFC`, `CSVSource`, etc.) and refactoring `LogicalSourceProvider` into a pure formulation/execution helper.

---

## Decision

### 1. Model Redesign: Decoupling `Source` from `LogicalSource`

#### Physical Source Interface & Implementations (`burp.model.source`)
Extract physical data sources into independent `AbstractPlanNode` implementations:
```java
public interface Source extends PlanNode {
    InputStream openStream() throws IOException;
    @Nullable Charset encoding();
    @Nullable Resource compression();
    Set<String> nullValues();
}
```

Concrete implementations:
- **`FileSource`**:
  ```java
  @RdfType("http://w3id.org/rml/RelativePathSource")
  public class FileSource extends AbstractPlanNode implements Source {
      @RdfProperty(Rml.path)
      public String path;

      @RdfProperty(Rml.encoding)
      public @Nullable String encoding;

      @RdfProperty(Rml.compression)
      public @Nullable Resource compression;

      @RdfProperty(Rml.nullValue)
      public Set<String> nullValues = new HashSet<>();
      ...
  }
  ```
- **`DatabaseSource`**: Declares `@RdfProperty` for JDBC URL, driver, credentials.
- **`SparqlSource`**: Declares `@RdfProperty` for endpoint URI.
- *Strict Standard Compliance*: Shortcut literals (such as raw string literals like `rml:source "data.json"`) are non-standard in modern RML. BURP strictly follows the standard and requires explicit source resources (`rml:RelativePathSource`, etc.).

#### Logical Source Hierarchy (`burp.model.ls`)
`AbstractLogicalSource` and `LogicalSource` represent data structuring and iteration over an underlying `Source`:

```java
public abstract class LogicalSource extends AbstractLogicalSource {
    @RdfMappedFrom({FileSource.class, DatabaseSource.class, SparqlSource.class})
    @RdfProperty(Rml.source)
    public Source source;

    @RdfProperty(Rml.referenceFormulation)
    public Resource referenceFormulation;

    @Override
    public Iterable<PlanNode> children() {
        return source != null ? List.of(source) : Collections.emptyList();
    }
}
```

Concrete subtypes declare formulation-specific properties and their supported source types declaratively via `@CompatibleSources`:
- **`JsonLogicalSource`** (replacing `JSONSourceRFC`):
  ```java
  @CompatibleSources({FileSource.class})
  public class JsonLogicalSource extends LogicalSource {
      @RdfProperty(Rml.iterator)
      public String iterator;

      @RdfProperty(BURP.jsonSchema)
      public @Nullable String jsonSchema;

      @Override
      public Iterable<Iteration> iterator() throws BurpException {
          // Evaluates JSONPath against source.openStream()
      }
  }
  ```
- **`CsvLogicalSource`** (replacing `CSVSource`):
  ```java
  @CompatibleSources({FileSource.class})
  public class CsvLogicalSource extends LogicalSource {
      @RdfProperty(CSVW.delimiter)
      public String delimiter = ",";
      ...
  }
  ```
- **`XmlLogicalSource`** (replacing `XMLSource`):
  ```java
  @CompatibleSources({FileSource.class})
  public class XmlLogicalSource extends LogicalSource {
      @RdfProperty(Rml.iterator)
      public String iterator; // XPath expression
      ...
  }
  ```
- **`RdbLogicalSource`** (replacing `RDBSource`):
  ```java
  @CompatibleSources({DatabaseSource.class})
  public class RdbLogicalSource extends LogicalSource {
      @RdfProperty(R2RML.tableName)
      public @Nullable String tableName;

      @RdfProperty(R2RML.sqlQuery)
      public @Nullable String sqlQuery;
  }
  ```

---

### 2. Polymorphic Type Resolution via SPI-Driven `LogicalSourceTypeDecider`
To allow extending BURP with custom logical sources without modifying core loader code, `LogicalSourceTypeDecider` delegates to a Java SPI (`LogicalSourceTypeProvider`):

```java
public interface LogicalSourceTypeProvider {
    /**
     * Checks if this provider handles the given logical source resource (e.g. by reference formulation or custom predicates).
     */
    boolean canHandle(Model model, Resource resource);

    /**
     * The concrete AST class to instantiate.
     */
    Class<? extends LogicalSource> getTargetClass();

    /**
     * Descriptive name for diagnostics and logging.
     */
    default String getName() {
        return getTargetClass().getSimpleName();
    }
}
```

#### Dispatch and Conflict Resolution:
When `LogicalSourceTypeDecider.decide(model, resource, targetClass)` is called:
1. Special case: if `resource.hasProperty(RML.viewOn)` -> returns `LogicalView.class`.
2. Discover all registered providers via `ServiceLoader.load(LogicalSourceTypeProvider.class)`.
3. Filter providers where `provider.canHandle(model, resource)` evaluates to `true`.
4. **Resolution Rules**:
   - **0 matching providers**: Returns `null` (or emits `RER.UnsupportedMapping` during validation).
   - **Exactly 1 matching provider**: Returns `matching.getFirst().getTargetClass()`.
   - **Multiple matching providers**:
     - A warning is recorded into `RmlExecutionReport` (with category `RER.Warning`):
       ```
       Multiple logical source providers declared capability for resource <res>: [ProviderA, ProviderB]. Selecting ProviderA.
       ```
     - BURP deterministically selects the first provider in iteration order.

#### Built-in Providers:
- `JsonLogicalSourceTypeProvider`: matches `rml:referenceFormulation` equal to `rml:JSONPath`.
- `CsvLogicalSourceTypeProvider`: matches `rml:referenceFormulation` equal to `rml:CSV`.
- `XmlLogicalSourceTypeProvider`: matches `rml:referenceFormulation` equal to `rml:XPath`.
- `RdbLogicalSourceTypeProvider`: matches `rml:referenceFormulation` equal to `rml:SQLTable` or `rml:SQLQuery` (or legacy `rml:SQL2008Table` / `rml:SQL2008Query` with a deprecation warning).

---

### 3. Source-to-LogicalSource Compatibility Validation & `RER.UnsupportedMapping`

Because physical sources and logical formulations are decoupled, invalid or unsupported pairings can be authored in mapping graphs (for example, pairing a `ql:JSONPath` logical source with a `DatabaseSource`, or pairing a SQL logical source with a `FileSource`).

#### Specification vs. Processor Fault (`UnsupportedMapping`):
In the RML specification, another processor could hypothetically support querying a database column using JSONPath or evaluating SQL queries over a CSV file directly. Therefore, an incompatible combination is **not** a specification violation (`RER.OutOfSpec`), but a **processor capability limitation**.

#### Validation Mechanics:
1. Each `LogicalSource` subtype declares its compatible `Source` types via the `@CompatibleSources` annotation:
   ```java
   @Target(ElementType.TYPE)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface CompatibleSources {
       Class<? extends Source>[] value();
   }
   ```
2. After the reflective loader maps both the `LogicalSource` and its child `Source`, a post-processor checks the allowed set:
   ```java
   CompatibleSources ann = logicalSource.getClass().getAnnotation(CompatibleSources.class);
   if (ann != null && logicalSource.source != null) {
       boolean compatible = Arrays.stream(ann.value())
               .anyMatch(allowed -> allowed.isInstance(logicalSource.source));
       if (!compatible) {
           // Emit RER.UnsupportedMapping
       }
   }
   ```
3. If an incompatible pairing is found:
   - BURP constructs an `RmlError` with:
     - **Error Type**: `RER.UnsupportedMapping` (indicating processor limitation, not spec invalidity).
     - **Origin**: The origin of the `rml:source` statement (`logicalSource.originOf("source")`).
     - **Message**:
       ```
       Unsupported mapping: Logical source <logicalSourceType> does not support physical source <sourceType>.
       ```
   - In `ValidationMode.STRICT`: Throws `BurpException(error)`.
   - In `ValidationMode.LENIENT`: Records the error into `RmlExecutionReport` and marks the logical source inert.

---

### 4. Refactoring `LogicalSourceProvider` into `ReferenceFormulationHandler`
`LogicalSourceProvider` currently mixes AST creation with expression evaluation (`parseStringPayload`, `buildReference`).
- **Remove AST creation** from the SPI (`create(...)` is removed).
- Retain an SPI `ReferenceFormulationHandler` focused strictly on reference resolution and payload parsing:
  - `buildReference(String expression, Origin origin)`
  - `parseStringPayload(String payload, String iterator, Origin origin)`
- Used by `IterableField` and expression evaluation without coupling to AST instantiation.

---

### 5. Statement Tracking & Provenance Elimination of Hacks
- Remove `recordLogicalSourceStatements(...)` in `RmlLoader`.
- Remove manual `getSourcePointers()` in `AbstractLogicalSource`.
- Both `LogicalSource` and `Source` inherit `getSourcePointers()` and `nodeRanges()` directly from `AbstractPlanNode`, populated automatically by `ReflectiveRdfObjectLoader`.

---

## Detailed Object Changes Summary

| Old Component | New Component | Changes |
| :--- | :--- | :--- |
| `AbstractLogicalSource.getSourcePointers()` | Base `AbstractPlanNode.getSourcePointers()` | Removed custom pointer collection hack; uses map-backed provenance. |
| `RmlLoader.recordLogicalSourceStatements()` | Deleted | Loader automatically records accessed statements during reflection. |
| `FileBasedLogicalSource` | `Source` interface + `FileSource` | Conflated file handling extracted into independent `Source` component. |
| `JSONSourceRFC` | `JsonLogicalSource` | Extends `LogicalSource`; references `Source source`; annotated with `@RdfProperty`. |
| `CSVSource` | `CsvLogicalSource` | Extends `LogicalSource`; references `Source source`; annotated with `@RdfProperty`. |
| `XMLSource` | `XmlLogicalSource` | Extends `LogicalSource`; references `Source source`; annotated with `@RdfProperty`. |
| `LogicalSourceFactory.create()` | `ReflectiveRdfObjectLoader.map(..., LogicalSource.class)` | Replaced procedural factory with declarative reflective loader. |
| `LogicalSourceTypeDecider` | SPI-driven `LogicalSourceTypeDecider` | Delegates to `LogicalSourceTypeProvider` SPI; emits warning and picks first on conflict. |
| (New Declarative Validation) | `@CompatibleSources({SourceClass.class})` | Declarative type check; emits `RER.UnsupportedMapping` on incompatible pairings. |
| `LogicalSourceProvider` | `ReferenceFormulationHandler` | Shifted from AST factory to reference builder and payload parsing SPI. |

---

## Consequences

### Positive
- **Architectural Uniformity**: 100% of BURP's AST is loaded declaratively via `ReflectiveRdfObjectLoader`.
- **Elimination of Workarounds**: No more recursive statement traversal or manual pointer hacking.
- **Open for Extension**: New logical sources can be plugged into BURP by implementing `LogicalSourceTypeProvider` via `ServiceLoader`.
- **Conflict Resilience**: Multi-provider clashes are logged with warnings and resolved deterministically.
- **Precise Error Semantics**: Incompatible source/formulation pairings report `RER.UnsupportedMapping` (processor fault) instead of generic crashes or incorrect spec violations (`RER.OutOfSpec`).
- **Independent AST Inspection**: Editors and language tools can target `rml:source` separately from `rml:iterator` and `rml:referenceFormulation`.

### Negative
- Breaking internal refactor: Existing tests and components directly referencing `JSONSourceRFC` or `FileBasedLogicalSource` must be updated to use `JsonLogicalSource` and `FileSource`.
- SPI Maintenance: Requires maintaining both `LogicalSourceTypeProvider` (for AST type resolution) and `ReferenceFormulationHandler` (for expression evaluation).
