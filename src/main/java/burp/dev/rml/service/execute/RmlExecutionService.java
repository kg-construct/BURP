package burp.dev.rml.service.execute;

import burp.dev.rml.RmlLanguageClient;
import burp.dev.rml.service.MappingWorkspace;
import burp.model.LogicalTarget;
import burp.model.RdfStatement;
import burp.model.TriplesMap;
import burp.processor.ExecutionOptions;
import burp.processor.RmlProcessor;
import org.apache.jena.rdf.model.Resource;
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

                RmlProcessor processor = new RmlProcessor(workspace.getCache());
                ExecutionOptions options = ExecutionOptions.builder()
                        .useCache(true)
                        .focusStructure(params.structure())
                        .baseIri(baseIri)
                        .build();

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

                processor.execute(document, options, (target, stmt) -> {
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

                Map<String, String> prefixMap = workspace.getPrefixMap(mappingFileUri);
                String reportMsg = String.format("Execution complete. Generated %d statements across %d targets.", totalStatements, targets.size());
                return new ExecuteResult(targets, reportMsg, prefixMap, logicalSources);

            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                return new ExecuteResult(Collections.emptyMap(), "Execution error: " + errMsg, Collections.emptyMap(), Collections.emptyMap());
            }
        });
    }
}
