package burp.model.fnml;

import burp.reporting.Origin;

import java.util.List;
import java.util.Map;

public interface RMLFunction {
    String getName();
    List<Return> apply(Map<String, Object> parameters, Origin origin);
}
