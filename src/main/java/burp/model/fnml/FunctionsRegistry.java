package burp.model.fnml;

import burp.model.context.EvaluationContext;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class FunctionsRegistry {

    public static final Map<String, RMLFunction> functions = loadFunctions();

    public static List<Return> execute(String function, Map<String, Object> inputValues, Origin expressionOrigin, EvaluationContext context) {
        RMLFunction f = functions.get(function);
        if (f != null) {
            return f.apply(inputValues, expressionOrigin, context);
        }
        throw new BurpException(
            new RmlError(
                "UnsupportedFunction: Function " + function + " not yet supported.",
                expressionOrigin,
                RER.UnsupportedFunction
            )
        );
    }

    private static Map<String, RMLFunction> loadFunctions() {
        ServiceLoader<RMLFunction> load = ServiceLoader.load(RMLFunction.class);
        Map<String, RMLFunction> functionsMap = new HashMap<>();
        
        for (RMLFunction f : load) {
            if (!functionsMap.containsKey(f.getName())) {
                functionsMap.put(f.getName(), f);
            }
        }
        
        return functionsMap;
    }
}
