# ADR-0011: OpenTelemetry Semantic Conventions and Architecture for RML Processors

## Status
Proposed

## Context
The RML Execution Report ([`RER`](https://w3id.org/dre/rer)) is an RDF-based vocabulary and taxonomy used in BURP to model processor execution, error diagnostics, AST pointers (`PTR`), statistics, and intermediate execution traces ([`rer.ttl`](file:///home/jakub/Documents/Dev/BURP/src/main/resources/vocabularies/rer.ttl)).

While RDF-native reporting is ideal for Knowledge Graph pipelines and semantic validation, production deployments, containerized execution, cloud ETL pipelines, and developer IDEs increasingly rely on **[OpenTelemetry (OTel)](https://opentelemetry.io/)** for unified observability across Traces, Metrics, and Logs.

To enable interoperability with modern Application Performance Monitoring (APM) systems (Jaeger, Grafana Tempo/Prometheus, OpenTelemetry Collector, Datadog), we need to:
1. Define a formal draft of the **OpenTelemetry Semantic Conventions for RML Processors (`rml.*`)**.
2. Adapt RER's rich ontology, error taxonomy, and AST mapping pointers into OTel signals (Resource, Traces, Metrics, Events/Logs).
3. Provide a clear architectural blueprint for BURP's runtime telemetry.

---

## Decision Drivers
- **Standard Observability Interoperability**: Seamless integration with OTLP collectors, Jaeger, Prometheus, and Grafana.
- **Semantic Fidelity**: Preserve RER's structural insights (AST statement pointers `PTR`, Logical Source references, Term Map derivations, and error taxonomies).
- **Scalability & High Cardinality Control**: Prevent trace explosion when executing mappings over millions of input records.
- **Dual-Model Support**: Retain RDF-based RER report generation while enabling native streaming OTLP export.

---

## Considered Options

### Option 1: Unstructured Log / JSON Serialization
- **Description**: Dump existing RER RDF triples or `RmlExecutionReport` as serialized JSON/text strings into generic log statements.
- **Pros**: Minimal implementation effort.
- **Cons**: No distributed trace graphs, poor queryability in APM dashboards, loses structured metrics.

### Option 2: Metrics-Only Export
- **Description**: Export counters and execution durations via Prometheus/OTel metrics only.
- **Pros**: Low overhead, simple metric definitions.
- **Cons**: Misses execution hierarchy, data lineage, fine-grained error context, and AST pointer locations.

### Option 3: Full OpenTelemetry Semantic Conventions for RML Processors [CHOSEN]
- **Description**: Define dedicated OpenTelemetry Semantic Conventions (`rml.*`) covering Resources, Traces (Hierarchical Spans), Metrics (Counters/Histograms), and Semantic Error Events with AST provenance.
- **Pros**:
  - Treats RML processors as first-class citizens in modern cloud-native observability stacks.
  - Establishes a standard candidate for community/W3C standardization across RML engines.
  - Unifies CLI batch execution, distributed pipelines, and real-time IDE diagnostics.
- **Cons**:
  - Requires managing trace sampling strategies for high-cardinality record iterations.

---

## Decision

We propose adopting the **OpenTelemetry Semantic Conventions for RML Processors**. Below is the complete specification draft.

---

# Draft Specification: OpenTelemetry Semantic Conventions for RML Processors

*Version: `0.1.0-draft`*  
*Namespace: `rml.*`*

```
+-----------------------------------------------------------------------------------------------+
|                                     OpenTelemetry Signals                                     |
+---------------------+-------------------------+-----------------------+-----------------------+
|      RESOURCE       |         TRACES          |        METRICS        |     EVENTS & LOGS     |
+---------------------+-------------------------+-----------------------+-----------------------+
| rml.processor.name  | rml.mapping.execute     | rml.statements.       | exception (Event)     |
| rml.processor.ver   | └── rml.triples_map.    |   generated (counter) | ├── rml.error.type    |
| rml.mapping.path    |       execute           | rml.iterations.       | └── rml.pointer.*     |
| rml.mapping.iri     |     ├── rml.source.     |   processed (counter) | rml.log.warning (Log) |
|                     |     │     fetch         | rml.execution.        | ├── rml.cause         |
|                     |     └── rml.term_map.   |   duration (hist)     | └── rml.solution      |
|                     |           evaluate      |                       |                       |
+---------------------+-------------------------+-----------------------+-----------------------+
```

---

## 1. Resource Attributes

Resource attributes describe the RML processor instance and static execution environment.

| Attribute | Type | Description | Example | Requirement |
| :--- | :--- | :--- | :--- | :--- |
| `service.name` | `string` | Processor service name | `"BURP"` | Required |
| `service.version` | `string` | Processor build version | `"0.2.0"` | Required |
| `rml.processor.name` | `string` | Formal RML Engine name | `"BURP"` | Required |
| `rml.processor.version` | `string` | Engine semantic version | `"0.2.0"` | Recommended |
| `rml.mapping.path` | `string` | File system or URI path to mapping | `"/workspace/mapping.ttl"` | Recommended |
| `rml.mapping.iri` | `string` | Target mapping document base IRI | `"http://example.org/rules#"` | Optional |
| `rml.spec.version` | `string` | Target W3C RML specification | `"RML-Core-2024"` | Optional |

---

## 2. Trace & Span Hierarchy

### 2.1 Span Structure
Execution is structured as a hierarchical DAG of Spans:

```
[Span: rml.mapping.execute]
   ├── [Span: rml.triples_map.execute] (triplesMap = <#PersonTM>)
   │      ├── [Span: rml.logical_source.fetch] (source = "persons.csv")
   │      ├── [Span: rml.subject_map.evaluate]
   │      └── [Span: rml.predicate_object_map.evaluate]
   │             ├── [Span: rml.fnml.execute] (function = grel:toUpperCase)
   │             └── [Span: rml.object_map.evaluate]
   └── [Span: rml.logical_target.write] (target = <#OutputDump>)
```

### 2.2 Standard Span Definitions

#### A. Root Mapping Span: `rml.mapping.execute`
* **SpanKind**: `INTERNAL`
* **Attributes**:
  * `rml.triples_maps.count` (`int`): Total TriplesMaps in execution plan.
  * `rml.execution.mode` (`string`): `"lenient"` | `"strict"` | `"debug"`.

#### B. TriplesMap Span: `rml.triples_map.execute`
* **SpanKind**: `INTERNAL`
* **Attributes**:
  * `rml.triples_map.id` (`string`): IRI or BlankNode identifier (e.g., `"http://example.org/rules#PersonTM"`).
  * `rml.logical_source.type` (`string`): `"CSV"` | `"JSONPath"` | `"XPath"` | `"SQL"` | `"SPARQL"`.

#### C. Logical Source Span: `rml.logical_source.fetch`
* **SpanKind**: `CLIENT` (if network/DB) or `INTERNAL` (if local file).
* **Attributes**:
  * `rml.source.iterator` (`string`): Iterator expression (e.g. `"$.records[*]"` or `"/root/item"`).
  * `rml.source.reference_formulation` (`string`): e.g. `ql:JSONPath`, `ql:CSV`.
  * `rml.source.uri` (`string`): Source file path, DB connection string, or endpoint URI.

#### D. TermMap Evaluation Span: `rml.term_map.evaluate`
* **SpanKind**: `INTERNAL`
* **Attributes**:
  * `rml.term_map.role` (`string`): `"subject"` | `"predicate"` | `"object"` | `"graph"`.
  * `rml.term_map.type` (`string`): `"template"` | `"reference"` | `"constant"`.
  * `rml.term_map.expression` (`string`): Raw template or reference expression (e.g. `"http://example.org/person/{ID}"`).
  * `rml.term_map.term_type` (`string`): `"IRI"` | `"Literal"` | `"BlankNode"`.

#### E. Function Execution Span: `rml.fnml.execute`
* **SpanKind**: `INTERNAL`
* **Attributes**:
  * `rml.fnml.function_iri` (`string`): e.g. `"http://users.ugent.be/~bjdmeest/function/grel.ttl#toUpperCase"`.
  * `rml.fnml.parameters.count` (`int`): Number of bound parameters.

---

## 3. High-Cardinality Sampling Strategy

To prevent generating millions of spans during batch mapping:

1. **Standard Mode (`default`)**:
   - Spans are generated down to `rml.triples_map.execute` and `rml.logical_source.fetch`.
   - Iteration evaluations are tracked in aggregate via Metrics counters.
   - Individual iteration errors generate **Span Events** on the parent `rml.triples_map.execute` span.
2. **Debug / Trace Mode (`sampling = 1.0` or on-demand)**:
   - Full child spans for individual record iterations (`rml.iteration.index`), used during IDE step-through, LSP `rml/trace`, and small testing payloads.

---

## 4. Error Mapping & Pointer Provenance

RER error classes ([`rer.ttl`](file:///home/jakub/Documents/Dev/BURP/src/main/resources/vocabularies/rer.ttl)) and AST Pointers (`PTR`) map to standard OpenTelemetry **Span Status**, **Exception Events**, and **Log Records**.

### 4.1 Flattened Error Taxonomy Scheme

| RER Class | `error.type` | `rml.error.category` |
| :--- | :--- | :--- |
| `rer:ReferenceFormulationExecutionError` | `ReferenceFormulationExecutionError` | `"execution/reference_formulation"` |
| `rer:ReferenceFormulationSyntaxError` | `ReferenceFormulationSyntaxError` | `"mapping/syntax/reference_formulation"` |
| `rer:SourceAccessError` | `SourceAccessError` | `"execution/source_access"` |
| `rer:TypeConversionError` | `TypeConversionError` | `"execution/data/type_conversion"` |
| `rer:IllTypedLiteralError` | `IllTypedLiteralError` | `"execution/data/ill_typed_literal"` |
| `rer:InvalidIRI` | `InvalidIRI` | `"execution/data/invalid_iri"` |
| `rer:OutOfSpec` | `OutOfSpec` | `"mapping/out_of_spec"` |
| `rer:UnsupportedFunction` | `UnsupportedFunction` | `"mapping/unsupported_function"` |

### 4.2 AST Statement Pointer (`PTR`) Attributes
When recording an error or diagnostic event, include the following semantic attributes:

```
rml.pointer.source_file        = "/workspace/mapping.ttl"
rml.pointer.range.start_line   = 24
rml.pointer.range.start_col    = 10
rml.pointer.range.end_line     = 24
rml.pointer.range.end_col      = 35
rml.pointer.statement_part     = "object"       # "subject" | "predicate" | "object" | "graph"
rml.pointer.raw_statement      = "<< :TM rml:subjectMap [ rml:template '...' ] >>"
```

### 4.3 Actionable Diagnostic Attributes

```
rml.error.potential_cause    = "Input JSON does not match the JSONPath expression."
rml.error.potential_solution = "Verify the JSON schema or use a fallback expression map."
rml.error.iteration_index    = 142
rml.error.raw_data_sample    = "{\"id\": null, \"name\": \"...\"}"
```

---

## 5. Metrics Specification

Instruments registered under the meter `io.rml.processor`:

| Instrument Name | Type | Unit | Description | Dimensions (Attributes) |
| :--- | :--- | :--- | :--- | :--- |
| `rml.statements.generated` | Counter | `{statement}` | Total generated RDF statements | `triples_map`, `target`, `term_type` |
| `rml.iterations.processed` | Counter | `{iteration}` | Number of records read from logical sources | `triples_map`, `source_type` |
| `rml.errors.count` | Counter | `{error}` | Total errors encountered | `error.type`, `category`, `triples_map` |
| `rml.warnings.count` | Counter | `{warning}` | Total warnings encountered | `warning.type`, `triples_map` |
| `rml.execution.duration` | Histogram | `ms` | End-to-end mapping execution latency | `mapping_file`, `status` |
| `rml.source.fetch_duration`| Histogram | `ms` | Time spent reading source data | `source_type`, `source_uri` |

---

## 6. Concrete Example: OTLP Span JSON Payload

```json
{
  "resource": {
    "attributes": [
      { "key": "service.name", "value": { "stringValue": "BURP" } },
      { "key": "service.version", "value": { "stringValue": "0.2.0" } },
      { "key": "rml.processor.name", "value": { "stringValue": "BURP" } },
      { "key": "rml.mapping.path", "value": { "stringValue": "file:///path/to/mapping.ttl" } }
    ]
  },
  "scopeSpans": [
    {
      "scope": { "name": "io.rml.processor", "version": "0.1.0" },
      "spans": [
        {
          "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
          "spanId": "00f067aa0ba902b7",
          "name": "rml.triples_map.execute",
          "kind": "SPAN_KIND_INTERNAL",
          "status": {
            "code": "STATUS_CODE_ERROR",
            "message": "Execution Error in JSONPath `$.film.title.value`"
          },
          "attributes": [
            { "key": "rml.triples_map.id", "value": { "stringValue": "http://example.org/PersonTM" } },
            { "key": "rml.logical_source.type", "value": { "stringValue": "JSONPath" } },
            { "key": "rml.statements.generated", "value": { "intValue": 142 } }
          ],
          "events": [
            {
              "name": "exception",
              "attributes": [
                { "key": "exception.type", "value": { "stringValue": "rer:ReferenceFormulationExecutionError" } },
                { "key": "exception.message", "value": { "stringValue": "Execution Error in JSONPath `$.film.title.value`" } },
                { "key": "rml.error.category", "value": { "stringValue": "execution/reference_formulation" } },
                { "key": "rml.error.potential_solution", "value": { "stringValue": "Check if $.film.title is missing in some records." } },
                { "key": "rml.error.iteration_index", "value": { "intValue": 42 } },
                { "key": "rml.pointer.source_file", "value": { "stringValue": "/workspace/mapping.ttl" } },
                { "key": "rml.pointer.range.start_line", "value": { "intValue": 65 } },
                { "key": "rml.pointer.range.start_col", "value": { "intValue": 14 } },
                { "key": "rml.pointer.statement_part", "value": { "stringValue": "object" } }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## Consequences

### Positive
- **Observability Standards**: RML engine pipelines can be observed alongside upstream databases, message queues, and downstream knowledge graphs using off-the-shelf tools (Jaeger, Prometheus, Grafana).
- **RER Interoperability**: Preserves full fidelity of RER taxonomies and AST pointer ranges while stripping away the need for RDF graph queries during runtime APM monitoring.
- **IDE & Dev Server Synergy**: The RML Dev Server and VS Code extension can consume standardized OTel streams to render line diagnostics, performance bottlenecks, and trace views.

### Negative / Trade-offs
- **High Cardinality**: Requires deliberate sampling controls to prevent large CSV/JSON datasets from overflowing trace backends.
- **Ontological Reasoning**: Graph-based ontology inference (e.g. SPARQL querying on `subClassOf*`) must be simulated via hierarchical category strings or companion taxonomies.

---

## Extension Points & Open Questions for Refinement
*(The following items are designated for further user feedback and domain tuning)*:
1. **Join Condition Spans (`rml:joinCondition`)**: Should child-parent joins be modeled as nested child spans or as **Span Links** connecting independent iterator traces?
2. **Streaming Sources (Kafka / WebSocket)**: How should continuous iteration windows be bounded into trace intervals?
3. **OpenLineage Companion Spec**: Should fine-grained intermediate node values (`TraceValue` in [`RmlTracer`](file:///home/jakub/Documents/Dev/BURP/src/main/java/burp/dev/rml/service/RmlTracer.java)) be exported via OpenLineage facets or retained as OTel Span Links?
