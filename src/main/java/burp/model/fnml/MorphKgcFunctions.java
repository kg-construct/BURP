package burp.model.fnml;

import burp.reporting.Origin;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MorphKgcFunctions {

    @AutoService(RMLFunction.class)
    public static class UUIDFunction implements RMLFunction {

        @Override
        public String getName() { return "https://github.com/morph-kgc/morph-kgc/function/built-in.ttl#uuid"; }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            return List.of(new Return(UUID.randomUUID().toString()));
        }
    }
}
