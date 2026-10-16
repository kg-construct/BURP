package burp.parse;

import burp.ls.RmlLogicalSourceContext;
import burp.parse.loader.RdfLoaderContext;
import burp.reporting.RmlExecutionReport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * RML-specific loader context combining generic RDF loader state with RML filesystem paths and execution reports.
 */
@NullMarked
public record RmlLoaderContext(
        Path mappingDirectory,
        Path currentWorkingDirectory,
        @Nullable RmlExecutionReport report,
        ValidationMode validationMode
) implements RmlLogicalSourceContext, RdfLoaderContext {

    private static final Path EMPTY_PATH = Path.of("");

    public RmlLoaderContext(
            @Nullable Path mappingDirectory,
            @Nullable Path currentWorkingDirectory,
            @Nullable RmlExecutionReport report,
            @Nullable ValidationMode validationMode
    ) {
        this.mappingDirectory = mappingDirectory != null ? mappingDirectory : EMPTY_PATH;
        this.currentWorkingDirectory = currentWorkingDirectory != null ? currentWorkingDirectory : EMPTY_PATH;
        this.report = report;
        this.validationMode = validationMode != null ? validationMode : ValidationMode.STRICT;
    }
}
