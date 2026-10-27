package burp.dev.rml.service.source;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Tabular payload for Logical Views and tabular Logical Sources (CSV, RDB, SPARQL).
 *
 * @param headers List of column header names.
 * @param columnTypes Inferred dominant datatype per column.
 * @param cellTypes Exact datatype tag per individual cell (e.g. "xsd:integer", "xsd:string", "iri", "bnode", "boolean").
 * @param rows 2D array of string values per row and column.
 */
public record TableData(
        List<String> headers,
        Map<String, String> columnTypes,
        List<List<@Nullable String>> cellTypes,
        List<List<@Nullable String>> rows
) {}
