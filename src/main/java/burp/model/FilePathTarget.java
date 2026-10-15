package burp.model;

import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

public record FilePathTarget(String path, Resource root) implements RMLTarget {

    public @NonNull File getResolvedPath(Path currentWorkingDirectory, @Nullable Path primaryMappingFile) {
        File resolvedPath;
        if (root.equals(RML.MappingDirectory)
                && primaryMappingFile != null
                && primaryMappingFile.getParent() != null) {
            resolvedPath = primaryMappingFile.getParent().resolve(path).toFile();
        } else {
            resolvedPath = currentWorkingDirectory.resolve(path).toFile();
        }
        return resolvedPath;
    }


    @Override
    public int hashCode() {
        return java.util.Objects.hash(path, root);
    }
}
