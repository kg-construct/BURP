package burp.dev.rml.service.execute;

import burp.dev.rml.RmlLanguageClient;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.ontology.OntologyConformanceChecker;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.dev.rml.service.validator.MappingValidator;
import burp.dev.rml.service.validator.RmlErrorDto;
import burp.model.LogicalTarget;
import burp.model.RdfPredicateObject;
import burp.model.RdfStatement;
import burp.model.TriplesMap;
import burp.model.context.EvaluationContext;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.parse.ValidationMode;
import burp.processor.EvaluatedStatementContext;
import burp.processor.ExecutionOptions;
import burp.processor.RmlProcessor;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import burp.reporting.Severity;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.*;

/**
 * Coordinates mapping execution for the RML Dev Server.
 * Supports prioritized structure ordering, real-time quad chunk notifications,
 * and per-target structured RDF materialization.
 */
public class RmlExecutionService {

    public CompletableFuture<ExecuteResult> execute(
            ExecuteParams params,
            MappingWorkspace workspace,
            @Nullable LanguageClient client
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String mappingFileUri = null;
                if (params.structure() != null) {
                    mappingFileUri = workspace.getMappingFileUri(params.structure());
                }

                if (mappingFileUri == null) {
                    // Fallback to first open document if available
                    throw new IllegalArgumentException("Could not determine mapping file URI for execution request");
                }

                var document = workspace.loadMappingWithStableIds(mappingFileUri);

                String baseIri = params.baseIri();
                if (baseIri == null || baseIri.isBlank()) {
                    if (workspace.getBaseIri() == null && client != null) {
                        try {
                            ConfigurationItem item = new ConfigurationItem();
                            item.setSection("rml");
                            List<Object> results = client.configuration(new ConfigurationParams(List.of(item)))
                                    .get(1, SECONDS);
                            if (results != null && !results.isEmpty()) {
                                workspace.updateSettings(results.getFirst());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    baseIri = workspace.getBaseIri();
                }

                var loaded = workspace.loadMappingDetailed(mappingFileUri, ValidationMode.LENIENT);
                Map<String, String> prefixMap = workspace.getPrefixMap(mappingFileUri);
                OntologyRegistry ontologyRegistry = workspace.getOntologyRegistry(loaded.model(), mappingFileUri);
                RmlExecutionReport report = new RmlExecutionReport();
                Consumer<EvaluatedStatementContext> observer = createConformanceObserver(report, ontologyRegistry, prefixMap);

                RmlProcessor processor = new RmlProcessor(workspace.getCache());
                ExecutionOptions options = ExecutionOptions.builder()
                        .useCache(true)
                        .focusStructure(params.structure())
                        .baseIri(baseIri)
                        .statementObserver(observer)
                        .build();
                EvaluationContext evalContext = EvaluationContext.createDefault()
                        .withReport(report)
                        .withBaseIri(baseIri)
                        .withValidationMode(ValidationMode.LENIENT)
                        .withStatementObserver(observer);

                String executionId = params.executionId() != null ? params.executionId() : UUID.randomUUID().toString();

                Map<LogicalTarget, List<RdfStatement>> targetStatements = new LinkedHashMap<>();
                Map<String, Long> chunkIndices = new HashMap<>();
                Map<String, List<RdfStatement>> streamingBuffers = new HashMap<>();

                // Progress notification helper
                Consumer<QuadChunkNotification> notifyQuadChunk = (client instanceof RmlLanguageClient rmlClient)
                        ? rmlClient::onQuadChunk
                        : (client instanceof Endpoint ep ? n -> ep.notify("rml/quadChunk", n) : null);

                Map<String, String> logicalSources = new LinkedHashMap<>();
                for (TriplesMap tm : document.triplesMaps) {
                    if (tm.logicalSource != null) {
                        String sourceId = tm.logicalSource.getId();
                        Resource subject = tm.logicalSource.subject;
                        if (subject != null) {
                            logicalSources.put(sourceId, subject.getLocalName());
                        } else {
                            logicalSources.put(sourceId, "TODO");
                        }
                    }
                }

                processor.execute(document, options, evalContext, (target, stmt) -> {
                    targetStatements.computeIfAbsent(target, k -> new ArrayList<>()).add(stmt);

                    if (notifyQuadChunk != null) {
                        String targetId = target.getId();
                        List<RdfStatement> buf = streamingBuffers.computeIfAbsent(targetId, k -> new ArrayList<>());
                        buf.add(stmt);

                        // Flush every 50 statements
                        if (buf.size() >= 50) {
                            long cIdx = chunkIndices.getOrDefault(targetId, 0L);
                            notifyQuadChunk.accept(new QuadChunkNotification(
                                    executionId,
                                    targetId,
                                    new ArrayList<>(buf),
                                    logicalSources,
                                    cIdx,
                                    false
                            ));
                            chunkIndices.put(targetId, cIdx + 1);
                            buf.clear();
                        }
                    }
                });

                Map<String, TargetExecutionResult> targets = new LinkedHashMap<>();
                long totalStatements = 0;

                for (Map.Entry<LogicalTarget, List<RdfStatement>> entry : targetStatements.entrySet()) {
                    LogicalTarget target = entry.getKey();
                    List<RdfStatement> stmts = entry.getValue();
                    String targetId = target.getId();
                    totalStatements += stmts.size();

                    targets.put(targetId, new TargetExecutionResult(
                            targetId,
                            LogicalTargetInfo.from(target),
                            stmts,
                            stmts.size()
                    ));

                    // Flush any remaining streaming buffer as last chunk
                    if (notifyQuadChunk != null) {
                        List<RdfStatement> buf = streamingBuffers.get(targetId);
                        List<RdfStatement> remaining = (buf != null && !buf.isEmpty()) ? new ArrayList<>(buf) : Collections.emptyList();
                        long cIdx = chunkIndices.getOrDefault(targetId, 0L);
                        notifyQuadChunk.accept(new QuadChunkNotification(
                                executionId,
                                targetId,
                                remaining,
                                logicalSources,
                                cIdx,
                                true
                        ));
                    }
                }

                // If no statements were generated at all, ensure at least one final notification is sent
                if (notifyQuadChunk != null && targetStatements.isEmpty()) {
                    notifyQuadChunk.accept(new QuadChunkNotification(
                            executionId,
                            "default",
                            Collections.emptyList(),
                            logicalSources,
                            0L,
                            true
                    ));
                }

                List<RmlErrorDto> errorDtos = report.getAggregatedLogs().stream()
                        .map(agg -> RmlErrorDto.from(agg, "BURP Execution"))
                        .toList();
                String reportMsg = String.format("Execution complete. Generated %d statements across %d targets.", totalStatements, targets.size());
                return new ExecuteResult(targets, reportMsg, prefixMap, logicalSources, errorDtos);

            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                RmlErrorDto errDto = e instanceof BurpException be
                        ? RmlErrorDto.from(be.getError(), "BURP Execution")
                        : new RmlErrorDto(
                                errMsg,
                                RER.UnexpectedError.getURI(),
                                Collections.emptyList(),
                                Severity.ERROR.name(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                0,
                                "BURP Execution"
                        );
                return new ExecuteResult(Collections.emptyMap(), "Execution error: " + errMsg, Collections.emptyMap(), Collections.emptyMap(), List.of(errDto));
            }
        });
    }

    private static Consumer<EvaluatedStatementContext> createConformanceObserver(
            RmlExecutionReport report,
            OntologyRegistry registry,
            Map<String, String> prefixMap
    ) {
        return ctx -> {
            if (!(ctx.evaluationResult() instanceof RdfPredicateObject po)) {
                return;
            }
            Set<Resource> subjectClasses = ctx.triplesMap().subjectMap != null
                    ? new LinkedHashSet<>(ctx.triplesMap().subjectMap.classes)
                    : Collections.emptySet();
            Resource predicate = ResourceFactory.createResource(po.predicate().uri());
            Term objTerm = po.object();
            Resource termType;
            String lexical = null;
            Resource litDatatype = null;

            if (objTerm instanceof LiteralTerm lit) {
                termType = RML.Literal;
                lexical = lit.value();
                if (lit.datatype() != null) {
                    litDatatype = ResourceFactory.createResource(lit.datatype().uri());
                }
            } else if (objTerm instanceof IRITerm) {
                termType = RML.IRI;
            } else {
                termType = RML.BlankNode;
            }

            List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                    subjectClasses, predicate, termType, null, lexical, litDatatype, registry, prefixMap
            );
            if (issues.isEmpty()) {
                return;
            }

            Origin pmOrigin = ctx.predicateMap() != null ? MappingValidator.resolveNodeOrigin(ctx.predicateMap(), "constant") : null;
            Origin omOrigin = ctx.objectMap() != null ? MappingValidator.resolveNodeOrigin(ctx.objectMap(), "constant") : null;
            for (var issue : issues) {
                Origin issueOrigin = issue.errorType().equals(RER.OntologyDomainMismatch)
                        ? pmOrigin
                        : (omOrigin != null ? omOrigin : pmOrigin);
                report.addLog(new RmlError(
                        issue.message(),
                        issueOrigin,
                        issue.errorType(),
                        null,
                        null,
                        issue.errorType().equals(RER.OntologyDomainMismatch) ? ctx.predicateMap() : ctx.objectMap(),
                        ctx.originLineage(),
                        ctx.evaluationResult()
                ));
            }
        };
    }
}
