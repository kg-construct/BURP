package burp.model;

import java.util.Set;

public interface RdfStatementLike {
    Set<LogicalTarget> targets();
}
