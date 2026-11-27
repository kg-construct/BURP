package burp.dev.rml.service.schema;

import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.model.AbstractLogicalSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for extracting schema information and candidate references from a logical source.
 */
@NullMarked
public interface SchemaProvider {

    boolean supports(@Nullable AbstractLogicalSource logicalSource);

    SchemaInfo extractSchema(AbstractLogicalSource logicalSource);
}
