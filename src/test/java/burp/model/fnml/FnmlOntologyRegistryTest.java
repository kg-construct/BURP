package burp.model.fnml;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FnmlOntologyRegistryTest {

    @Test
    public void testLoadedFunctionsFromAllOntologies() {
        Set<Resource> functions = FnmlOntologyRegistry.getDeclaredFunctions();
        assertThat(functions).isNotEmpty();

        // 1. grel.ttl
        Resource grelSubstr = ResourceFactory.createResource("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_substring");
        assertThat(FnmlOntologyRegistry.isDeclaredFunction(grelSubstr)).isTrue();
        List<FnmlParameter> substrParams = FnmlOntologyRegistry.getParameters(grelSubstr);
        assertThat(substrParams).extracting(FnmlParameter::name).contains("input value", "from", "to");

        // 2. functions_idlab.ttl
        Resource idlabConcat = ResourceFactory.createResource("https://w3id.org/imec/idlab/function#concat");
        assertThat(FnmlOntologyRegistry.isDeclaredFunction(idlabConcat)).isTrue();
        List<FnmlParameter> concatParams = FnmlOntologyRegistry.getParameters(idlabConcat);
        assertThat(concatParams).extracting(FnmlParameter::name).contains("input string", "other input string", "delimiter");

        // 3. burp.ttl
        Resource burpToSafeIri = ResourceFactory.createResource("http://BURP.noname/function/toSafeIRI");
        assertThat(FnmlOntologyRegistry.isDeclaredFunction(burpToSafeIri)).isTrue();
        List<FnmlParameter> burpParams = FnmlOntologyRegistry.getParameters(burpToSafeIri);
        assertThat(burpParams).extracting(FnmlParameter::name).contains("input value");

        // 4. example.ttl
        Resource exParseURL = ResourceFactory.createResource("http://example.com/functions/parseURL");
        assertThat(FnmlOntologyRegistry.isDeclaredFunction(exParseURL)).isTrue();
        List<FnmlParameter> exParams = FnmlOntologyRegistry.getParameters(exParseURL);
        assertThat(exParams).extracting(FnmlParameter::name).contains("string");
        String formattedExParam = FnmlOntologyRegistry.formatParameterHint(
                ResourceFactory.createResource("http://example.com/functions/stringParameter"),
                Map.of("xsd", "http://www.w3.org/2001/XMLSchema#")
        );
        assertThat(formattedExParam).isEqualTo("string (xsd:string)");

        // 5. morph-kgc.ttl
        Resource morphUuid = ResourceFactory.createResource("https://github.com/morph-kgc/morph-kgc/function/built-in.ttl#uuid");
        assertThat(FnmlOntologyRegistry.isDeclaredFunction(morphUuid)).isTrue();
    }

    @Test
    public void testLoadedOutputsAndPredicateDetection() {
        Resource grelSubstr = ResourceFactory.createResource("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_substring");
        List<FnmlOutput> substrOutputs = FnmlOntologyRegistry.getOutputs(grelSubstr);
        assertThat(substrOutputs).isNotEmpty();
        FnmlOutput out = substrOutputs.getFirst();
        assertThat(out.iri().getURI()).isEqualTo("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut");
        assertThat(out.predicateIri()).isNotNull();
        assertThat(out.predicateIri().getURI()).isEqualTo("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOutput");
        assertThat(out.isUsingPredicateWhenOutputDefined(out.predicateIri())).isTrue();
        assertThat(out.isUsingPredicateWhenOutputDefined(out.iri())).isFalse();

        FnmlParameter param = FnmlOntologyRegistry.getParameters(grelSubstr).getFirst();
        assertThat(param.predicateIri()).isNotNull();
        assertThat(param.isUsingPredicateWhenParamDefined(param.predicateIri())).isTrue();
        assertThat(param.isUsingPredicateWhenParamDefined(param.iri())).isFalse();
    }
}
