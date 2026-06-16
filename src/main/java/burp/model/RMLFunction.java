package burp.model;

import burp.reporting.Origin;

import java.util.List;
import java.util.Map;

public interface RMLFunction {
    List<Return> apply(Map<String, Object> parameters, Origin origin);
}
