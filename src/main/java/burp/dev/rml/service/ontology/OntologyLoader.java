package burp.dev.rml.service.ontology;

import burp.dev.rml.service.MappingWorkspace;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.OWL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/// Discovers and loads target ontologies for a workspace and mapping document.
@NullMarked
public class OntologyLoader {

    public static OntologyRegistry loadForWorkspace(
            MappingWorkspace workspace,
            @Nullable Model mappingModel,
            @Nullable String mappingFileUri
    ) {
        OntologyRegistry registry = new OntologyRegistry();
        Set<Path> loadedPaths = new HashSet<>();

        // 1. From mapping owl:imports (<> owl:imports <./ontology.ttl>)
        if (mappingModel != null) {
            mappingModel.listStatements(null, OWL.imports, (RDFNode) null).forEachRemaining(s -> {
                if (s.getObject().isURIResource()) {
                    String importUri = s.getObject().asResource().getURI();
                    Path resolved = resolvePath(importUri, workspace, mappingFileUri);
                    if (resolved != null && Files.exists(resolved) && loadedPaths.add(resolved)) {
                        tryLoad(registry, resolved);
                    }
                }
            });
        }

        // 2. From workspace settings (rml.ontologies and swls.ontologies)
        for (String ontPathStr : workspace.getConfiguredOntologies()) {
            Path resolved = resolvePath(ontPathStr, workspace, mappingFileUri);
            if (resolved != null && Files.exists(resolved) && loadedPaths.add(resolved)) {
                tryLoad(registry, resolved);
            }
        }

        return registry;
    }

    private static @Nullable Path resolvePath(String pathOrUri, MappingWorkspace workspace, @Nullable String mappingFileUri) {
        try {
            URI uri = URI.create(pathOrUri);
            if (uri.isAbsolute() && "file".equalsIgnoreCase(uri.getScheme())) {
                Path p = Path.of(uri);
                if (Files.exists(p)) return p;
            }
        } catch (Exception ignored) {
        }

        Path direct = workspace.normalizePath(pathOrUri);
        if (direct.isAbsolute() && Files.exists(direct)) {
            return direct;
        }

        // Relative to mapping file
        if (mappingFileUri != null) {
            Path mappingPath = workspace.normalizePath(mappingFileUri);
            if (mappingPath.getParent() != null) {
                Path relativeToDoc = mappingPath.getParent().resolve(pathOrUri).normalize();
                if (Files.exists(relativeToDoc)) return relativeToDoc;

                try {
                    URI uri = URI.create(pathOrUri);
                    String rawPath = uri.getPath();
                    if (rawPath != null && !rawPath.isBlank()) {
                        String filename = Path.of(rawPath).getFileName().toString();
                        Path sibling = mappingPath.getParent().resolve(filename);
                        if (Files.exists(sibling)) return sibling;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Relative to workspace cwd
        Path relativeToCwd = workspace.getCurrentWorkingDirectory().resolve(pathOrUri).normalize();
        if (Files.exists(relativeToCwd)) return relativeToCwd;

        return null;
    }

    private static void tryLoad(OntologyRegistry registry, Path path) {
        try {
            registry.loadOntology(path);
        } catch (Exception ignored) {
        }
    }
}
