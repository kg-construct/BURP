package burp.reporting;
 
import org.jspecify.annotations.Nullable;
import java.nio.file.Path;

public interface RDFPointer {
    default @Nullable Path file() {
        return null;
    }
}
