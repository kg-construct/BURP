package burp.dev.rml.service.source;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Preview result for a Logical View or Logical Source.
 *
 * @param structureId The unique short identifier of the previewed structure.
 * @param structureResource The RDF resource (IRI or blank node string) of the previewed structure, if any.
 * @param sourceType String indicating source type (e.g. "LogicalView", "JSONSourceRFC", "XMLSource", "CSVSource").
 * @param format Preview format: "table", "json", "xml".
 * @param table Tabular payload when format is "table".
 * @param document Formatted document payload when format is "json" or "xml".
 * @param offset Current item offset.
 * @param limit Requested item count limit.
 * @param totalItems Total items count estimate or exact count.
 * @param totalExact True if totalItems is an exact count.
 * @param hasMore True if more items are available beyond offset + limit.
 * @param diagnostics Diagnostics, warnings, or error messages encountered during preview generation.
 */
public record SourceDataResult(
        String structureId,
        @Nullable String structureResource,
        String sourceType,
        String format,
        @Nullable TableData table,
        @Nullable DocumentData document,
        int offset,
        int limit,
        int totalItems,
        boolean totalExact,
        boolean hasMore,
        List<String> diagnostics
) {}
