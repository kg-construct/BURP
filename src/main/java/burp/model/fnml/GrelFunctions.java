package burp.model.fnml;

import burp.Main;
import burp.model.rdf.LiteralTerm;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import com.google.auto.service.AutoService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.WordUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.XSD;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GrelFunctions {

    private static String getValueString(Map<String, Object> parameters, String name) {
        Object val = parameters.get(name);
        if (val instanceof LiteralTerm) {
            return ((LiteralTerm) val).value();
        } else if (val != null) {
            return val.toString();
        }
        return null;
    }

    private static String hashWithAlgorithm(String value, String algorithm) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @AutoService(RMLFunction.class)
    public static class BooleanAndFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_and";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm a = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_a");
            LiteralTerm b = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_b");
            boolean out = (a != null && Boolean.TRUE.equals(a.booleanOrNull())) && (b != null && Boolean.TRUE.equals(b.booleanOrNull()));
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class BooleanNotFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_not";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm a = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool");
            boolean out = !(a != null && Boolean.TRUE.equals(a.booleanOrNull()));
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class BooleanOrFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_or";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm a = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_a");
            LiteralTerm b = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_b");
            boolean out = (a != null && Boolean.TRUE.equals(a.booleanOrNull())) || (b != null && Boolean.TRUE.equals(b.booleanOrNull()));
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class BooleanXorFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_xor";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm a = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_a");
            LiteralTerm b = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_b");
            boolean valA = a != null && Boolean.TRUE.equals(a.booleanOrNull());
            boolean valB = b != null && Boolean.TRUE.equals(b.booleanOrNull());
            boolean out = valA ^ valB;
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringChompFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_chomp";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String f = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sep");
            String out = StringUtils.removeEnd(s, f);
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringContainsFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_contains";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String f = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub");
            boolean out = s != null && f != null && s.contains(f);
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringContainsPatternFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_contains_pattern";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String p = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_regex");
            boolean out = s != null && p != null && s.matches(p);
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class EndsWithFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#endsWith";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String f = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub");
            boolean out = s != null && f != null && s.endsWith(f);
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class EscapeFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#escape";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String p = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#modeParam");
            String modeLower = p != null ? p.toLowerCase(Locale.getDefault()) : null;
            String out = null;
            if (s != null && modeLower != null) {
                switch (modeLower) {
                    case "html":
                        out = StringEscapeUtils.escapeHtml4(s);
                        break;
                    case "xml":
                        out = StringEscapeUtils.escapeXml11(s);
                        break;
                    case "csv":
                        out = StringEscapeUtils.escapeCsv(s);
                        break;
                    case "javascript":
                        out = StringEscapeUtils.escapeEcmaScript(s);
                        break;
                    case "url":
                        out = URLEncoder.encode(s, StandardCharsets.UTF_8);
                        break;
                    default:
                        throw new BurpException(new RmlError(String.format("Mode %s not supported in GREL's escape function.", p), origin, RER.FunctionExecutionError));
                }
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class LengthFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_length";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            Integer out = s != null ? s.length() : null;
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class MathAbsFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#math_abs";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm s = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n");
            Object out = null;
            if (s != null && s.datatype() != null) {
                String duri = s.datatype().uri();
                String lex = s.value();
                if (XSD.integer.getURI().equals(duri) || XSD.xint.getURI().equals(duri)) {
                    out = Math.abs(Integer.parseInt(lex));
                } else if (XSD.xdouble.getURI().equals(duri)) {
                    out = Math.abs(Double.parseDouble(lex));
                } else if (XSD.xlong.getURI().equals(duri)) {
                    out = Math.abs(Long.parseLong(lex));
                } else if (XSD.xfloat.getURI().equals(duri)) {
                    out = Math.abs(Float.parseFloat(lex));
                } else if (XSD.xshort.getURI().equals(duri)) {
                    out = Math.abs(Short.parseShort(lex));
                } else {
                    throw new BurpException(new RmlError(String.format("Mode %s not supported in GREL's abs function.", s), origin, RER.FunctionExecutionError));
                }
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_decimal", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class MathCeilFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#math_ceil";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm s = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n");
            Object out = null;
            if (s != null && s.datatype() != null) {
                String duri = s.datatype().uri();
                String lex = s.value();
                if (XSD.integer.getURI().equals(duri) || XSD.xint.getURI().equals(duri)) {
                    out = (int) Math.ceil(Double.parseDouble(lex));
                } else if (XSD.xdouble.getURI().equals(duri)) {
                    out = (int) Math.ceil(Double.parseDouble(lex));
                } else if (XSD.xlong.getURI().equals(duri)) {
                    out = (int) Math.ceil(Double.parseDouble(lex));
                } else if (XSD.xfloat.getURI().equals(duri)) {
                    out = (int) Math.ceil(Double.parseDouble(lex));
                } else if (XSD.xshort.getURI().equals(duri)) {
                    out = (int) Math.ceil(Double.parseDouble(lex));
                } else {
                    throw new BurpException(new RmlError(String.format("Mode %s not supported in GREL's ceil function.", s), origin, RER.FunctionExecutionError));
                }
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class MathFloorFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#math_floor";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            LiteralTerm s = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n");
            Object out = null;
            if (s != null && s.datatype() != null) {
                String duri = s.datatype().uri();
                String lex = s.value();
                if (XSD.integer.getURI().equals(duri) || XSD.xint.getURI().equals(duri)) {
                    out = (int) Math.floor(Double.parseDouble(lex));
                } else if (XSD.xdouble.getURI().equals(duri)) {
                    out = (int) Math.floor(Double.parseDouble(lex));
                } else if (XSD.xlong.getURI().equals(duri)) {
                    out = (int) Math.floor(Double.parseDouble(lex));
                } else if (XSD.xfloat.getURI().equals(duri)) {
                    out = (int) Math.floor(Double.parseDouble(lex));
                } else if (XSD.xshort.getURI().equals(duri)) {
                    out = (int) Math.floor(Double.parseDouble(lex));
                } else {
                    throw new BurpException(new RmlError(String.format("Mode %s not supported in GREL's floor function.", s), origin, RER.FunctionExecutionError));
                }
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StartsWithFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#startsWith";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String f = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub");
            boolean out = s != null && f != null && s.startsWith(f);
            return List.of(new Return(out, Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringGetFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_get";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            LiteralTerm from = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from");
            LiteralTerm to = (LiteralTerm) parameters.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to");
            String out = null;
            if (s != null && from != null && from.intOrNull() != null) {
                int f = from.intOrNull();
                if (to != null && to.intOrNull() != null) {
                    int t = to.intOrNull();
                    if (t > 0) out = s.substring(f, t);
                    else out = s.substring(f, s.length() + t);
                } else {
                    out = s.substring(f, f + 1);
                }
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringReplaceFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_replace";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String f = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_find");
            String r = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#param_replace");
            String out = null;
            if (s != null && f != null) {
                out = s.replaceAll(f, r != null ? r : "");
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringStripFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_strip";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String out = s != null ? s.trim() : null;
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringSubstringFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_substring";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            String string = getValueString(parameters, valueParam);
            if (string == null) {
                throw new BurpException(new RmlError(
                        "Missing parameter " + valueParam + " in string_substring, received: " + parameters.get(valueParam),
                        origin,
                        RER.FunctionExecutionError
                ));
            }

            String pIntIFrom = "http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from";
            LiteralTerm fromTerm = (LiteralTerm) parameters.get(pIntIFrom);
            Integer from = fromTerm != null ? fromTerm.intOrNull() : null;
            if (from == null) {
                throw new BurpException(new RmlError("Missing or invalid parameter " + pIntIFrom + " in string_substring, received: " + parameters.get(pIntIFrom), origin, RER.FunctionExecutionError));
            }

            String pIntIOptTo = "http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to";
            LiteralTerm toTerm = (LiteralTerm) parameters.get(pIntIOptTo);
            Integer to = toTerm != null ? toTerm.intOrNull() : null;

            String out = null;
            try {
                if (to != null) {
                    if (to > 0) out = string.substring(from, to);
                    else out = string.substring(from, string.length() + to);
                } else {
                    out = string.substring(from);
                }
            } catch (StringIndexOutOfBoundsException e) {
                Map<Property, Object> parametersMap = new HashMap<>();
                parametersMap.put(ResourceFactory.createProperty(valueParam), string);
                parametersMap.put(ResourceFactory.createProperty(pIntIFrom), from);
                if (to != null) {
                    parametersMap.put(ResourceFactory.createProperty(pIntIOptTo), to);
                }
                Main.report.getErrors().add(
                        new RmlError(
                                "String index out of bounds [" + from + ", " + (to != null ? to : "null") + "] in string (length " + string.length() + ") " + string,
                                origin,
                                RER.FunctionExecutionError,
                                e,
                                parametersMap
                        )
                );
            }
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringTrimFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_trim";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String out = s != null ? s.trim() : null;
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ToLowerCaseFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#toLowerCase";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String out = s != null ? s.toLowerCase(Locale.getDefault()) : null;
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ToUpperCaseFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#toUpperCase";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            String string = getValueString(parameters, valueParam);
            if (string == null) {
                Main.report.getErrors().add(
                        new RmlError(
                                "Missing parameter " + valueParam + " in " + getName() + " function, received: " + parameters,
                                origin,
                                RER.FunctionExecutionError
                        )
                );
            }
            String out = string != null ? string.toUpperCase(Locale.getDefault()) : null;
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class ToTitleCaseFunction implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#toTitleCase";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String s = getValueString(parameters, "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam");
            String out = s != null ? WordUtils.capitalizeFully(s.toLowerCase(Locale.getDefault())) : null;
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringSha1Function implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_sha1";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            String string = getValueString(parameters, valueParam);
            if (string == null) {
                throw new BurpException(new RmlError("Missing parameter: " + valueParam, origin, RER.FunctionExecutionError));
            }
            String out = hashWithAlgorithm(string, "SHA-1");
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }

    @AutoService(RMLFunction.class)
    public static class StringSha256Function implements RMLFunction {
        @Override
        public String getName() {
            return "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_sha256";
        }

        @Override
        public List<Return> apply(Map<String, Object> parameters, Origin origin) {
            String valueParam = "http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam";
            String string = getValueString(parameters, valueParam);
            if (string == null) {
                throw new BurpException(new RmlError("Missing parameter: " + valueParam, origin, RER.FunctionExecutionError));
            }
            String out = hashWithAlgorithm(string, "SHA-256");
            return List.of(new Return(out, out != null ? Map.of("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out) : Map.of()));
        }
    }
}
