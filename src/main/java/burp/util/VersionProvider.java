package burp.util;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.IVersionProvider;

import java.io.InputStream;
import java.util.Properties;

/// Provides the BURP application version for Picocli CLI (`--version`, `-V`).
///
/// Resolves the version from `/burp.properties` filtered by Maven during build.
@NullMarked
public class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        String version = getVersionString();
        return version != null ? new String[]{version} : new String[0];
    }

    /// Resolves the current application version from filtered `/burp.properties`, or null if not found.
    public static @Nullable String getVersionString() {
        try (InputStream in = VersionProvider.class.getResourceAsStream("/burp.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("processor.version");
                if (version != null && !version.isBlank() && !version.startsWith("${")) {
                    return version.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
