package burp.ls;

import burp.reporting.RmlExecutionReport;

import java.nio.file.Path;

/**
 * Context provided to LogicalSourceProvider for source and reference formulation initialization.
 */
public interface RmlLogicalSourceContext {
    Path mappingDirectory();
    Path currentWorkingDirectory();
    RmlExecutionReport report();

    static RmlLogicalSourceContext of(Path mappingDirectory, Path currentWorkingDirectory) {
        return new SimpleRmlLogicalSourceContext(mappingDirectory, currentWorkingDirectory, null);
    }

    static RmlLogicalSourceContext of(Path mappingDirectory, Path currentWorkingDirectory, RmlExecutionReport report) {
        return new SimpleRmlLogicalSourceContext(mappingDirectory, currentWorkingDirectory, report);
    }
}

record SimpleRmlLogicalSourceContext(Path mappingDirectory, Path currentWorkingDirectory, RmlExecutionReport report) implements RmlLogicalSourceContext {}
