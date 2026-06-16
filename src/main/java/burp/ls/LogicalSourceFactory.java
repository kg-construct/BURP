package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public class LogicalSourceFactory {

    private static final ServiceLoader<LogicalSourceProvider> LOADER = ServiceLoader.load(LogicalSourceProvider.class);

    private LogicalSourceFactory() {}

    public static LogicalSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory) {
        Statement stmt = ls.getProperty(RML.referenceFormulation);
        Resource referenceFormulation = stmt.getObject().asResource();
        for (LogicalSourceProvider provider : LOADER) {
            if (provider.supports(referenceFormulation)) {
                return provider.create(ls, mappingDirectory, currentWorkingDirectory);
            }
        }

        String supported = LOADER.stream()
                .map(p -> p.type().getName())
                .collect(Collectors.joining(", "));
        String supportedMessage = !supported.isEmpty() ? "Are supported: " + supported + "."
                : "None are supported, provide a `burp.ls.LogicalSourceProvider` in class path.";

        throw new BurpException(
            new RmlError(
                "Reference formulation not supported: " + referenceFormulation + ". " + supportedMessage,
                new Origin(stmt, StatementPart.Object),
                RER.Error
            )
        );
    }

    public static List<Iteration> changeIterator(
        String iterationAsString,
        Resource referenceFormulation,
        String iterator,
        Origin referenceFormulationOrigin
    ) {
        for (LogicalSourceProvider provider : LOADER) {
            if (provider.supports(referenceFormulation)) {
                return provider.parseStringPayload(iterationAsString, iterator, referenceFormulationOrigin);
            }
        }

        throw new BurpException(
            new RmlError(
                "Reference formulation not supported for nested string iterations: " + referenceFormulation,
                referenceFormulationOrigin,
                RER.UnsupportedMapping
            )
        );
    }

    public static List<Iteration> changeIterator(
        String iterationAsString,
        Resource referenceFormulation,
        String iterator
    ) {
        return changeIterator(iterationAsString, referenceFormulation, iterator, null);
    }

    public static Reference buildReference(
        Resource referenceFormulation, String reference, Origin origin, Origin referenceFormulationOrigin
    ) {
        for (LogicalSourceProvider provider : LOADER) {
            if (provider.supports(referenceFormulation)) {
                return provider.buildReference(reference, origin, referenceFormulationOrigin);
            }
        }

        throw new BurpException(
            new RmlError(
                "Reference formulation not supported for nested references: " + referenceFormulation,
                referenceFormulationOrigin,
                RER.UnsupportedMapping
            )
        );
    }

    public static Reference buildReference(
        Resource referenceFormulation, String reference, Origin origin
    ) {
        return buildReference(referenceFormulation, reference, origin, null);
    }
}
