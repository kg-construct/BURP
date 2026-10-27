package burp.dev.rml.service.completion;

import burp.dev.rml.service.schema.*;
import burp.model.*;
import burp.model.lv.IterableField;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves contextual logical sources and coordinates modular schema providers
 * (LogicalViewSchemaProvider, JsonSchemaProvider, CsvSchemaProvider, JsonSampledSchemaProvider).
 */
@NullMarked
public final class ReferenceSchemaResolver {

    public static final SqlSchemaProvider SQL_PROVIDER = new SqlSchemaProvider();

    private ReferenceSchemaResolver() {
    }

    public record SchemaInfo(
            String referenceFormulation,
            Map<String, String> candidateValues,
            Map<String, ReferenceCompletionOrigin> candidateOrigins,
            boolean hasKnownSchemaOrData
    ) {
    }

    public static @Nullable AbstractLogicalSource resolveContextualLogicalSource(@Nullable PlanNode targetNode) {
        if (targetNode == null) {
            return null;
        }
        LocalReferenceScope localScope = targetNode instanceof LocalReferenceScope ls ? ls : targetNode.ancestor(LocalReferenceScope.class);
        if (localScope != null && localScope.getLogicalSource() != null) {
            return localScope.getLogicalSource();
        }
        if (targetNode instanceof AbstractLogicalSource ls) {
            return ls;
        }
        return null;
    }

    public static SchemaInfo extractSchemaInfo(
            @Nullable PlanNode targetNode,
            AbstractLogicalSource ls,
            @Nullable Integer iterationSampleLimit
    ) {
        JsonSampledSchemaProvider sampledProvider = new JsonSampledSchemaProvider(iterationSampleLimit);

        IterableField enclosingIter = targetNode != null ? targetNode.ancestor(IterableField.class) : null;
        if (enclosingIter != null && sampledProvider.supports(ls)) {
            return sampledProvider.extractSchema(ls, targetNode);
        }

        Resource rf = ls.getReferenceFormulation();
        String refFormulation = (rf != null && rf.getURI() != null) ? rf.getURI() : Rml.JSONPath;

        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;

        List<SchemaProvider> providers = List.of(
                new LogicalViewSchemaProvider(),
                new JsonSchemaProvider(),
                new CsvSchemaProvider(),
                SQL_PROVIDER,
                sampledProvider
        );

        for (SchemaProvider provider : providers) {
            if (provider.supports(ls)) {
                SchemaInfo info = provider.extractSchema(ls);
                if (info.hasKnownSchemaOrData()) {
                    hasKnownSchemaOrData = true;
                }
                for (Map.Entry<String, String> entry : info.candidateValues().entrySet()) {
                    String existing = candidateValues.get(entry.getKey());
                    if (existing == null || existing.isEmpty()) {
                        candidateValues.put(entry.getKey(), entry.getValue());
                    }
                }
                for (Map.Entry<String, ReferenceCompletionOrigin> entry : info.candidateOrigins().entrySet()) {
                    candidateOrigins.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }

        return new SchemaInfo(refFormulation, candidateValues, candidateOrigins, hasKnownSchemaOrData);
    }
}
