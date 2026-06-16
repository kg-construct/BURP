package burp.model.fnmlutil;

import burp.Main;
import burp.model.RMLFunction;
import burp.model.Return;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class FunctionsRegistry {

    public static Map<String, RMLFunction> functions = loadFunctions();

    public static List<Return> execute(String function, Map<String, Object> map, Origin expressionOrigin) {
        RMLFunction f = functions.get(function);
        if (f != null) {
            return f.apply(map, expressionOrigin);
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
            if (!functionsMap.containsKey(f.toString())) {
                functionsMap.put(f.toString(), f);
            } else {
                if (Main.report != null && Main.report.getErrors() != null) {
                    Main.report.getErrors().add(
                        new RmlError(
                            "Function " + f.toString() + " already exists, not loading from service loader " + f + ".",
                            null,
                            RER.Warning
                        )
                    );
                }
            }
        }
        
        if (Main.report != null && Main.report.getErrors() != null) {
            Main.report.getErrors().add(
                new RmlError(
                    "The following " + functionsMap.size() + " functions were loaded: " + String.join(", ", functionsMap.keySet()),
                    null,
                    RER.Information
                )
            );
        }
        
        return functionsMap;
    }
}
