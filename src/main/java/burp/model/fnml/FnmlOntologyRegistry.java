package burp.model.fnml;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.*;

/**
 * Loads FnO function ontologies (GREL, IDLab, BURP, Example, Morph-KGC)
 * and provides lookup of functions and parameter definitions.
 */
@NullMarked
public final class FnmlOntologyRegistry {

    private static final Map<Resource, List<FnmlParameter>> FUNCTIONS = new HashMap<>();
    private static final Map<Resource, FnmlParameter> PARAMETER_LOOKUP = new HashMap<>();
    private static final Map<Resource, List<FnmlOutput>> FUNCTION_OUTPUTS = new HashMap<>();
    private static final Map<Resource, FnmlOutput> OUTPUT_LOOKUP = new HashMap<>();

    static {
        loadOntologies();
    }

    private FnmlOntologyRegistry() {}

    private static void loadOntologies() {
        Model model = ModelFactory.createDefaultModel();
        loadResource(model, "fnml/grel.ttl");
        loadResource(model, "fnml/functions_idlab.ttl");
        loadResource(model, "fnml/burp.ttl");
        loadResource(model, "fnml/example.ttl");
        loadResource(model, "fnml/morph-kgc.ttl");

        String queryString = """
                PREFIX fno: <https://w3id.org/function/ontology#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                SELECT ?function ?param ?predicate ?name ?label ?required ?type WHERE {
                  ?function a fno:Function .
                  OPTIONAL {
                    ?function fno:expects ?list .
                    ?list rdf:rest*/rdf:first ?param .
                    OPTIONAL { ?param fno:predicate ?predicate . }
                    OPTIONAL { ?param fno:name ?name . }
                    OPTIONAL { ?param rdfs:label ?label . }
                    OPTIONAL { ?param fno:required ?required . }
                    OPTIONAL { ?param fno:type ?type . }
                  }
                }
                """;

        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                if (!soln.contains("function")) continue;

                Resource funcRes = soln.getResource("function");
                FUNCTIONS.putIfAbsent(funcRes, new ArrayList<>());

                if (soln.contains("param") && soln.get("param").isURIResource()) {
                    Resource paramRes = soln.getResource("param");
                    Resource predicateRes = (soln.contains("predicate") && soln.get("predicate").isURIResource())
                            ? soln.getResource("predicate")
                            : null;

                    String name = null;
                    if (soln.contains("name")) {
                        name = soln.getLiteral("name").getString();
                    } else if (soln.contains("label")) {
                        name = soln.getLiteral("label").getString();
                    } else {
                        name = (predicateRes != null ? predicateRes : paramRes).getLocalName();
                    }

                    boolean isRequired;
                    if (soln.contains("required")) {
                        RDFNode reqNode = soln.get("required");
                        isRequired = reqNode.isLiteral() && Boolean.parseBoolean(reqNode.asLiteral().getLexicalForm());
                    } else {
                        String local = (predicateRes != null ? predicateRes : paramRes).getLocalName().toLowerCase();
                        isRequired = !local.contains("opt") && !local.contains("optional");
                    }

                    Resource typeRes = null;
                    if (soln.contains("type") && soln.get("type").isURIResource()) {
                        typeRes = soln.getResource("type");
                    }

                    FnmlParameter param = new FnmlParameter(paramRes, predicateRes, name, isRequired, typeRes);
                    List<FnmlParameter> paramsList = FUNCTIONS.get(funcRes);
                    if (paramsList.stream().noneMatch(p -> p.matches(param.iri()))) {
                        paramsList.add(param);
                    }

                    PARAMETER_LOOKUP.putIfAbsent(paramRes, param);
                    if (predicateRes != null) {
                        PARAMETER_LOOKUP.putIfAbsent(predicateRes, param);
                    }
                }
            }
        }

        String returnsQueryString = """
                PREFIX fno: <https://w3id.org/function/ontology#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                SELECT ?function ?output ?predicate ?name ?label ?type WHERE {
                  ?function a fno:Function .
                  OPTIONAL {
                    ?function fno:returns ?list .
                    ?list rdf:rest*/rdf:first ?output .
                    OPTIONAL { ?output fno:predicate ?predicate . }
                    OPTIONAL { ?output fno:name ?name . }
                    OPTIONAL { ?output rdfs:label ?label . }
                    OPTIONAL { ?output fno:type ?type . }
                  }
                }
                """;

        Query returnsQuery = QueryFactory.create(returnsQueryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(returnsQuery, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                if (!soln.contains("function")) continue;

                Resource funcRes = soln.getResource("function");
                FUNCTION_OUTPUTS.putIfAbsent(funcRes, new ArrayList<>());

                if (soln.contains("output") && soln.get("output").isURIResource()) {
                    Resource outputRes = soln.getResource("output");
                    Resource predicateRes = (soln.contains("predicate") && soln.get("predicate").isURIResource())
                            ? soln.getResource("predicate")
                            : null;

                    String name;
                    if (soln.contains("name")) {
                        name = soln.getLiteral("name").getString();
                    } else if (soln.contains("label")) {
                        name = soln.getLiteral("label").getString();
                    } else {
                        name = (predicateRes != null ? predicateRes : outputRes).getLocalName();
                    }

                    Resource typeRes = null;
                    if (soln.contains("type") && soln.get("type").isURIResource()) {
                        typeRes = soln.getResource("type");
                    }

                    FnmlOutput output = new FnmlOutput(outputRes, predicateRes, name, typeRes);
                    List<FnmlOutput> outputsList = FUNCTION_OUTPUTS.get(funcRes);
                    if (outputsList.stream().noneMatch(o -> o.matches(output.iri()))) {
                        outputsList.add(output);
                    }

                    OUTPUT_LOOKUP.putIfAbsent(outputRes, output);
                    if (predicateRes != null) {
                        OUTPUT_LOOKUP.putIfAbsent(predicateRes, output);
                    }
                }
            }
        }
    }

    private static void loadResource(Model model, String resourcePath) {
        try (InputStream in = FnmlOntologyRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                RDFDataMgr.read(model, in, Lang.TURTLE);
            }
        } catch (Exception ignored) {
        }
    }

    public static Set<Resource> getDeclaredFunctions() {
        return Collections.unmodifiableSet(FUNCTIONS.keySet());
    }

    public static boolean isDeclaredFunction(Resource function) {
        return FUNCTIONS.containsKey(function);
    }

    public static List<FnmlParameter> getParameters(Resource function) {
        return FUNCTIONS.getOrDefault(function, Collections.emptyList());
    }

    public static @Nullable FnmlParameter findMatchingParameter(Resource function, Resource parameter) {
        List<FnmlParameter> params = FUNCTIONS.get(function);
        if (params == null) return null;
        for (FnmlParameter p : params) {
            if (p.matches(parameter)) {
                return p;
            }
        }
        return null;
    }

    public static boolean isValidParameter(Resource function, Resource parameter) {
        List<FnmlParameter> params = FUNCTIONS.get(function);
        if (params == null || params.isEmpty()) {
            return true;
        }
        for (FnmlParameter p : params) {
            if (p.matches(parameter)) {
                return true;
            }
        }
        return false;
    }

    public static List<FnmlParameter> getMissingRequiredParameters(Resource function, Set<Resource> boundParameters) {
        List<FnmlParameter> params = FUNCTIONS.get(function);
        if (params == null || params.isEmpty()) {
            return Collections.emptyList();
        }

        List<FnmlParameter> missing = new ArrayList<>();
        for (FnmlParameter p : params) {
            if (!p.required()) continue;

            boolean bound = false;
            for (Resource b : boundParameters) {
                if (p.matches(b)) {
                    bound = true;
                    break;
                }
            }

            if (!bound) {
                missing.add(p);
            }
        }

        return missing;
    }

    public static @Nullable FnmlParameter findParameter(Resource parameter) {
        FnmlParameter exact = PARAMETER_LOOKUP.get(parameter);
        if (exact != null) return exact;

        for (Map.Entry<Resource, FnmlParameter> entry : PARAMETER_LOOKUP.entrySet()) {
            if (entry.getKey().getURI().equals(parameter.getURI())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static @Nullable String formatParameterHint(Resource parameter, @Nullable Map<String, String> prefixMap) {
        FnmlParameter param = findParameter(parameter);
        if (param == null) return null;

        String name = param.name();
        if (param.typeIri() != null && param.typeIri().getURI() != null) {
            String shortType = RmlEvaluationResultFormatter.compactIri(param.typeIri().getURI(), prefixMap);
            return name + " (" + shortType + ")";
        }
        return name;
    }

    public static List<FnmlOutput> getOutputs(Resource function) {
        return FUNCTION_OUTPUTS.getOrDefault(function, Collections.emptyList());
    }

    public static @Nullable FnmlOutput findMatchingOutput(Resource function, Resource output) {
        List<FnmlOutput> outputs = FUNCTION_OUTPUTS.get(function);
        if (outputs == null) return null;
        for (FnmlOutput o : outputs) {
            if (o.matches(output)) {
                return o;
            }
        }
        return null;
    }

    public static boolean isValidOutput(Resource function, Resource output) {
        List<FnmlOutput> outputs = FUNCTION_OUTPUTS.get(function);
        if (outputs == null || outputs.isEmpty()) {
            return true;
        }
        for (FnmlOutput o : outputs) {
            if (o.matches(output)) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable FnmlOutput findOutput(Resource output) {
        FnmlOutput exact = OUTPUT_LOOKUP.get(output);
        if (exact != null) return exact;

        for (Map.Entry<Resource, FnmlOutput> entry : OUTPUT_LOOKUP.entrySet()) {
            if (entry.getKey().getURI().equals(output.getURI())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
