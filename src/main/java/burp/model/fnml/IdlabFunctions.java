package burp.model.fnml;

import burp.model.rdf.LiteralTerm;
import burp.model.rdf.RdfSeqTerm;
import burp.reporting.Origin;
import com.google.auto.service.AutoService;

import java.util.*;
import java.util.stream.Collectors;

public class IdlabFunctions {

    public static String toValueString(Object obj) {
        if (obj instanceof LiteralTerm literalTerm) {
            return literalTerm.value();
        }
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }

    private static Map<String, Object> createReturnMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @AutoService(RMLFunction.class)
    public static class IdlabToUpperCaseURLFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#toUpperCaseURL";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            Object strParam = parameters.get("https://w3id.org/imec/idlab/function#str");
            String strVal = toValueString(strParam);
            String strUpper = strVal != null ? strVal.toUpperCase(Locale.getDefault()) : null;

            String out;
            if (strUpper == null || strUpper.startsWith("HTTP://")) {
                out = strUpper;
            } else {
                out = "http://" + strUpper;
            }

            Return r = new Return(out, createReturnMap("https://w3id.org/imec/idlab/function#_stringOut", out));
            return List.of(r);
        }
    }

    @AutoService(RMLFunction.class)
    public static class IdlabRandomFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#random";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String out = UUID.randomUUID().toString();
            Return r = new Return(out, createReturnMap("https://w3id.org/imec/idlab/function#_stringOut", out));
            return List.of(r);
        }
    }

    @AutoService(RMLFunction.class)
    public static class IdlabEqualFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#equal";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            var val1 = parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String val1Str = toValueString(val1);
            var val2 = parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam2");
            String val2Str = toValueString(val2);
            boolean out = Objects.equals(val1Str, val2Str);
            Return r = new Return(out, createReturnMap("https://w3id.org/imec/idlab/function#_boolOut", out));
            return List.of(r);
        }
    }

    @AutoService(RMLFunction.class)
    public static class IdlabConcatFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#concat";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String str = toValueString(parameters.get("https://w3id.org/imec/idlab/function#str"));
            String otherStr = toValueString(parameters.get("https://w3id.org/imec/idlab/function#otherStr"));

            Object delimObj = parameters.get("https://w3id.org/imec/idlab/function#delimiter");
            if (delimObj == null) {
                delimObj = parameters.get("https://w3id.org/imec/idlab/function#separator");
            }
            String delimiter = toValueString(delimObj);
            if (delimiter == null) {
                delimiter = "";
            }

            List<String> list = new ArrayList<>();
            if (str != null) list.add(str);
            if (otherStr != null) list.add(otherStr);

            String out = null;
            if (!list.isEmpty()) {
                out = String.join(delimiter, list);
            }

            Return r = new Return(out, createReturnMap("https://w3id.org/imec/idlab/function#_stringOut", out));
            return List.of(r);
        }
    }

    @AutoService(RMLFunction.class)
    public static class IdlabConcatSequenceFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#concatSequence";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String delimiter = toValueString(parameters.get("https://w3id.org/imec/idlab/function#_delimiter"));
            if (delimiter == null) {
                delimiter = "";
            }

            Object raw = parameters.get("https://w3id.org/imec/idlab/function#_seq");
            List<?> seq;
            if (raw instanceof RdfSeqTerm) {
                seq = ((RdfSeqTerm) raw).getElements();
            } else if (raw instanceof Iterable) {
                List<Object> list = new ArrayList<>();
                ((Iterable<?>) raw).forEach(list::add);
                seq = list;
            } else if (raw instanceof Object[]) {
                seq = Arrays.asList((Object[]) raw);
            } else if (raw == null) {
                seq = List.of();
            } else {
                seq = List.of(raw);
            }

            String out = seq.stream()
                    .map(IdlabFunctions::toValueString)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(delimiter));

            Return r = new Return(out, createReturnMap("https://w3id.org/imec/idlab/function#_stringOut", out));
            return List.of(r);
        }
    }

    @AutoService(RMLFunction.class)
    public static class IdlabIfFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#IF";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            var condition = parameters.get("https://w3id.org/imec/idlab/function#boolParameter");
            var expr = parameters.get("https://w3id.org/imec/idlab/function#expressionParameter");

            Boolean isTrue;
            if (condition instanceof Boolean aBoolean) {
                isTrue = aBoolean;
            } else if (condition instanceof LiteralTerm literalTerm) {
                isTrue = literalTerm.booleanOrNull();
            } else {
                isTrue = condition != null && Boolean.parseBoolean(condition.toString());
            }

            if (Boolean.TRUE.equals(isTrue)) {
                return List.of(new Return(expr));
            } else {
                return List.of();
            }
        }
    }


    // FIXME: This function is missing from the actual vocabulary but is required for the RML-FNML test case.
    @AutoService(RMLFunction.class)
    public static class AlwaysReturnsABCFunction implements RMLFunction {
        @Override
        public String getName() {
            return "https://w3id.org/imec/idlab/function#alwaysReturnsABC";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String out = "ABC";
            return List.of(new Return(out, createReturnMap("https://w3id.org/imec/idlab/function#_stringOut", out)));
        }
    }
}
