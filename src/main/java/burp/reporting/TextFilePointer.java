package burp.reporting;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

@NullMarked
public record TextFilePointer(Path path, @Nullable PointRange range) implements RDFPointer {

    @Override
    public Path file() {
        return path;
    }

    @Override
    public String toString() {
        return "TextFilePointer[path=" + path + ", range=" + range + "]";
    }
}
