package burp.dev.rml.service.schema;

import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.ls.CSVIteration;
import burp.ls.CSVSource;
import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.vocabularies.Rml;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts candidate references and sample values from CSV file headers.
 * When CSVW is implemented, declared column definitions can be extracted directly here.
 */
@NullMarked
public class CsvSchemaProvider implements SchemaProvider {

    @Override
    public boolean supports(@Nullable AbstractLogicalSource logicalSource) {
        return logicalSource instanceof CSVSource;
    }

    @Override
    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource) {
        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;

        if (logicalSource instanceof CSVSource csvSource) {
            try {
                var it = csvSource.iterator();
                if (it != null) {
                    var iterIterator = it.iterator();
                    if (iterIterator.hasNext()) {
                        Iteration sampleIter = iterIterator.next();
                        if (sampleIter instanceof CSVIteration csvIter) {
                            for (var entry : csvIter.map.entrySet()) {
                                candidateValues.putIfAbsent(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
                                candidateOrigins.putIfAbsent(entry.getKey(), new ReferenceCompletionOrigin("header", null, "header", null));
                            }
                            hasKnownSchemaOrData = !candidateValues.isEmpty();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return new SchemaInfo(Rml.CSV, candidateValues, candidateOrigins, hasKnownSchemaOrData);
    }
}
