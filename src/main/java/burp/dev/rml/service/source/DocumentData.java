package burp.dev.rml.service.source;

import java.util.List;

/**
 * Formatted document payload for document-oriented Logical Sources (JSON, XML).
 *
 * @param language Language identifier ("json", "xml").
 * @param items List of formatted text items (one per iteration record).
 */
public record DocumentData(
        String language,
        List<String> items
) {}
