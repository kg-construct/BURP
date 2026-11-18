package burp.dev.rml.service.schema;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.model.AbstractLogicalSource;
import burp.model.lv.Field;
import burp.model.lv.IterableField;
import burp.model.lv.LogicalView;
import burp.model.lv.ViewJoin;
import burp.vocabularies.Rml;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts declared field names specifically from rml:LogicalView definitions.
 */
@NullMarked
public class LogicalViewSchemaProvider implements SchemaProvider {

    @Override
    public boolean supports(@Nullable AbstractLogicalSource logicalSource) {
        return logicalSource instanceof LogicalView;
    }

    @Override
    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource) {
        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;

        if (logicalSource instanceof LogicalView lv) {
            collectFields(lv.fields, candidateValues, candidateOrigins);
            for (ViewJoin join : lv.getJoins()) {
                collectFields(join.fields, candidateValues, candidateOrigins);
            }
            hasKnownSchemaOrData = !candidateValues.isEmpty();
        }

        return new SchemaInfo(Rml.LogicalView, candidateValues, candidateOrigins, hasKnownSchemaOrData);
    }

    private void collectFields(
            @Nullable List<Field> fields,
            Map<String, String> candidateValues,
            Map<String, ReferenceCompletionOrigin> candidateOrigins
    ) {
        if (fields == null) {
            return;
        }
        for (Field field : fields) {
            String absName = field.getAbsoluteFieldName();
            if (!absName.isBlank()) {
                String ptrStr = field.getSourcePointers().stream()
                        .map(PlanNodeDescriptor::getRdfPointerString)
                        .findFirst()
                        .orElse(null);
                candidateValues.put(absName, "");
                candidateOrigins.put(absName, new ReferenceCompletionOrigin("logicalView", field.getIri(), null, ptrStr));
            }
            if (field instanceof IterableField iterField) {
                collectFields(iterField.fields, candidateValues, candidateOrigins);
            }
        }
    }
}
