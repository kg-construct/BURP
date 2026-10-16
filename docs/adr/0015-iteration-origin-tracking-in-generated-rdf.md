# ADR-0015: Iteration Origin Tracking in Generated RDF Statements

## Status

Accepted

## Context

When the users inspect provenance by clicking the trace action (🔍) on individual quads in the Execution Table, with large volume of output (e.g. 6,000+ quads).
Currently, the server-side trace pipeline (`TracePipelineEngine.traceResult`) does not know which iteration produced the target quad. 

Consequently:
1. It executes a full trace over **all** iterations of the mapping document (Since we don't know which generated it).
2. It evaluates every AST `PlanNode` for all records, using the explicite dependency system, building intermediate `TraceValue` objects.
3. It serializes the entire graph.
4. It traverses backwards from the target quad to slice out the relevant subgraph (`sliceProvenance`).

This makes single-quad tracing significantly slower than full mapping execution.
In contrast, tracing a single known iteration (`traceIteration`) would be near instant.

To achieve single-quad provenance inspection, each generated RDF statement must carry metadata identifying the exact iterations that produced it. 
Because joins (classical RML `ReferencingObjectMap` joins, RML-LV `ViewJoin`) merge records from multiple sources, a statement can derive from a primary iteration as well as joined/parent iterations.

## Decision Drivers

- Fast traces to avoid waiting even more than the execution itself.
- Join lineage with both the primary iteration and any joined iterations that are also needed for the trace reconstruction.
- Direct Attachment on `RdfStatement`, keep it simple without side lookup tables.
- identifiers should be composed of (logical source ID + iteration index).
- Execution still produces N-Quads with the iteration origin data next to each quad.

## Considered Options

- Traces information computed fully during execution.
- Execution cache all node intermediary results making trace fast to recompute (false because would still need to iterate on the cache)
- keep iteration id, to only recompute the related iterations of a quad.

- transport of execution results options see below.

## Detailed Design

### Composite Iteration Identifier

An iteration is uniquely identified by the logical source identifier and the iteration's 0-based offset within that source:

```java
public record IterationId(String logicalSourcePlanNodeId, int index) {}
```

### Statement Iteration Lineage

An `RdfStatement` carries its origin metadata directly:

```java
public record StatementOrigins(IterationId primary, List<IterationId> joined) {}
```

Add `origins` to `RmlEvaluationResult`:

```java
public interface RmlEvaluationResult {
   Set<LogicalTarget> targets();
   StatementOrigins iterationOrigin();
}
```

### Tracking during generations 

1. Add id to `Iteration`.
2. ExpressionMap asking for value gets the id from the Iteration add it to the primary id.
3. Other nodes transforming results keep the ids intact
4. Joins will keep ids and add parent id as joined.

### Execution transport in `rml/execute`

We were previously sending in `rml/quadChunk` per target and execution id, up to 50 n-quads in a string.
To attach to each quad its iteration origin, we could:
- use rdf 1.2
- attach a list of up to 50 iteration origins to each chunk.
- or create a list of tuples of nquad + iteration origin.
- or create a list of tuples of (s, p, o, g, iteration origin)

### Trace request

Traces from quad will include the iteration origin. And thus we could use a special data context only with those iterations.
Using `MultiSourceDataContext` that we will probably need to adapt slightly.

## Consequences

### Positive
- Faster traces that only compute what's needed.
- Experiment with tracing during execution to see if there is a way to expand.

- The List layout in the ExecutionApp will be able to use trace id to generate the list and not previous heuristic improving prescision of the layout.
- Warnings/errors will be able to give iteration index as origin information. For example rer:FunctionExecutionError we will know which iteration cause bad execution.

### Negative / Trade-offs
- Slight memory increases during execution to hold `StatementOrigins` references.
- Payload size over JSON-RPC increases when transporting `List<StatementWithOrigins>`. (Can be made opt-in via extension parameters if needed).
