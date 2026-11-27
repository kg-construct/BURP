package burp.model.fnml;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * idlab-fn:toUpperCaseURL takes its string by the fno:Parameter IRI (idlab-fn:_str), as
 * in RMLFNMLTC0003, 0011, 0031 and 0061 since kg-construct/rml-fnml#85, or by its
 * fno:predicate (idlab-fn:str), as in earlier mappings.
 */
public class IdlabParameterTest {

    private static final String IDLAB = "https://w3id.org/imec/idlab/function#";

    private static Object upperCaseURL(String parameter) {
        return new IdlabFunctions.IdlabToUpperCaseURLFunction()
                .apply(Map.of(parameter, "example.com/venus"), null).getFirst().defaultValue;
    }

    @Test
    public void testParameterIri() {
        assertEquals("http://EXAMPLE.COM/VENUS", upperCaseURL(IDLAB + "_str"));
    }

    @Test
    public void testParameterPredicate() {
        assertEquals("http://EXAMPLE.COM/VENUS", upperCaseURL(IDLAB + "str"));
    }
}
