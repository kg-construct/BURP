package burp.model.fnmlutil;

import burp.model.RMLFunction;
import burp.model.Return;
import burp.model.rdf.Term;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.util.Util;
import burp.vocabularies.RER;
import com.google.auto.service.AutoService;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuiltInFunctions {

    private static Term getRequiredParameter(Map<String, Object> parameters, String name, Origin origin) {
        Object val = parameters.get(name);
        if (val instanceof Term) return (Term) val;
        throw new BurpException(new RmlError("Missing parameter: " + name, origin, RER.Error));
    }

    @AutoService(RMLFunction.class)
    public static class HelloWorldFunction implements RMLFunction {

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            return List.of(new Return("Hello World!"));
        }
    }

    @AutoService(RMLFunction.class)
    public static class SchemaFunction implements RMLFunction {

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = parameters.get("http://example.com/functions/stringParameter").toString();
            String out = "https://schema.org/" + s;
            return List.of(new Return(out, Map.of("http://example.com/functions/stringOutput", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ParseURL implements RMLFunction {

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            try {
                String s = parameters.get("http://example.com/functions/stringParameter").toString();
                URI uri = new URI(s);

                String protocol = uri.getScheme();
                String domain = uri.getHost();
                String path = uri.getPath();

                Return r = new Return(
                        path,
                        Map.of(
                                "http://example.com/functions/stringOutput", path,
                                "http://example.com/functions/protocolOutput", protocol != null ? protocol : "",
                                "http://example.com/functions/domainOutput", domain != null ? domain : ""
                        )
                );
                return List.of(r);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @AutoService(RMLFunction.class)
    public static class UUIDFunction implements RMLFunction {

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            return List.of(new Return(UUID.randomUUID().toString()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ToSafeIRIFunction implements RMLFunction {

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            Term term = getRequiredParameter(parameters, valueParam, origin);
            String string = term.toString();
            String out = string != null ? Util.toIRISafe(string) : null;
            
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ToSafeURIFunction implements RMLFunction {

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            Term term = getRequiredParameter(parameters, valueParam, origin);
            String string = term.toString();
            String out = string != null ? Util.toURISafe(string) : null;
            
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }
}
