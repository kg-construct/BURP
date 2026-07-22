package burp.reporting;

import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

public record TextFilePointer(Path path, PointRange range) implements RDFPointer {

    @Override
    public @NonNull String toString() {
        return "TextFilePointer[path=" + path + ", range=" + range + "]";
    }
}
