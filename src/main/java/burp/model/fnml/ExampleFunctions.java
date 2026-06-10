package burp.model.fnml;

import burp.reporting.Origin;
import com.google.auto.service.AutoService;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class ExampleFunctions {

    @AutoService(RMLFunction.class)
    public static class HelloWorldFunction implements RMLFunction {

        @Override
        public String getName() { return "http://example.com/functions/helloworld"; }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            return List.of(new Return("Hello World!"));
        }
    }

    @AutoService(RMLFunction.class)
    public static class SchemaFunction implements RMLFunction {

        @Override
        public String getName() { return "http://example.com/functions/schema"; }

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
        public String getName() { return "http://example.com/functions/parseURL"; }

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
}
