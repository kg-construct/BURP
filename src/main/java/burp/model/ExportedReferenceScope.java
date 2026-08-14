package burp.model;

import burp.reporting.Origin;

public interface ExportedReferenceScope extends PlanNode {
    /**
     * Builds an exported reference for external querying.
     * When a TriplesMap uses a LogicalView as its logical source, references from the TriplesMap
     * are evaluated against the exported fields of the LogicalView, not its internal iterators.
     */
    Reference buildExportedReference(String reference, Origin origin);
}
