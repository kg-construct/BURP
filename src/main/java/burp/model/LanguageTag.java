package burp.model;

import java.util.Set;

public record LanguageTag(String tag, Set<LogicalTarget> targets) {
}
