package burp.model.fnml;

import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.util.Util;
import burp.vocabularies.RER;
import com.google.auto.service.AutoService;

import java.util.List;
import java.util.Map;

public class BurpFunctions {

    private static Term getRequiredParameter(Map<String, Object> parameters, String name, Origin origin) {
        Object val = parameters.get(name);
        if (val instanceof Term) return (Term) val;
        throw new BurpException(new RmlError("Missing parameter: " + name, origin, RER.Error));
    }

    @AutoService(RMLFunction.class)
    public static class ToSafeIRIFunction implements RMLFunction {

        @Override
        public String getName() {
            return "http://BURP.noname/function/toSafeIRI";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            Term term = getRequiredParameter(parameters, valueParam, origin);
            String termValue = (term instanceof LiteralTerm literal) ? literal.value() : term.toString();
            String out = termValue != null ? Util.toIRISafe(termValue) : null;

            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ToSafeURIFunction implements RMLFunction {

        @Override
        public String getName() {
            return "http://BURP.noname/function/toSafeURI";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            Term term = getRequiredParameter(parameters, valueParam, origin);
            String termValue = (term instanceof LiteralTerm literal) ? literal.value() : term.toString();
            String out = termValue != null ? Util.toURISafe(termValue) : null;

            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }
}
