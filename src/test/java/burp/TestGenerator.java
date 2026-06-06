package burp;

import burp.model.TriplesMap;
import burp.parse.Parse;
import java.io.File;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 *
 */
public class TestGenerator {

    private static String base = "./src/test/resources/rml-core/";

    @ParameterizedTest
    @ValueSource(strings = {
        "RMLTC0000-CSV",
        "RMLTC0000-JSON",
        "RMLTC0000-XML",
        "RMLTC0001a-CSV",
        "RMLTC0001a-JSON",
        "RMLTC0001a-XML",
        "RMLTC0001b-CSV",
        "RMLTC0001b-JSON",
        "RMLTC0001b-XML",
        "RMLTC0002a-CSV",
        "RMLTC0002a-JSON",
        "RMLTC0002a-XML",
        "RMLTC0002b-CSV",
        "RMLTC0002b-JSON",
        "RMLTC0002b-XML",
        "RMLTC0003c-CSV",
        "RMLTC0003c-JSON",
        "RMLTC0003c-XML",
        "RMLTC0004a-CSV",
        "RMLTC0004a-JSON",
        "RMLTC0004a-XML",
        "RMLTC0005a-CSV",
        "RMLTC0005a-JSON",
        "RMLTC0005a-XML",
        "RMLTC0006a-CSV",
        "RMLTC0006a-JSON",
        "RMLTC0006a-XML",
        "RMLTC0007a-CSV",
        "RMLTC0007a-JSON",
        "RMLTC0007a-XML",
        "RMLTC0007b-CSV",
        "RMLTC0007b-JSON",
        "RMLTC0007b-XML",
        "RMLTC0007c-CSV",
        "RMLTC0007c-JSON",
        "RMLTC0007c-XML",
        "RMLTC0007d-CSV",
        "RMLTC0007d-JSON",
        "RMLTC0007d-XML",
        "RMLTC0007e-CSV",
        "RMLTC0007e-JSON",
        "RMLTC0007e-XML",
        "RMLTC0007f-CSV",
        "RMLTC0007f-JSON",
        "RMLTC0007f-XML",
        "RMLTC0007g-CSV",
        "RMLTC0007g-JSON",
        "RMLTC0007g-XML",
        "RMLTC0008a-CSV",
        "RMLTC0008a-JSON",
        "RMLTC0008a-XML",
        "RMLTC0008b-CSV",
        "RMLTC0008b-JSON",
        "RMLTC0008b-XML",
        "RMLTC0008c-CSV",
        "RMLTC0008c-JSON",
        "RMLTC0008c-XML",
        "RMLTC0009a-CSV",
        "RMLTC0009a-JSON",
        "RMLTC0009a-XML",
        "RMLTC0009b-CSV",
        "RMLTC0009b-JSON",
        "RMLTC0009b-XML",
        "RMLTC0010a-CSV",
        "RMLTC0010a-JSON",
        "RMLTC0010b-CSV",
        "RMLTC0010b-JSON",
        "RMLTC0010b-XML",
        "RMLTC0010c-CSV",
        "RMLTC0010c-JSON",
        "RMLTC0010c-XML",
        "RMLTC0011b-CSV",
        "RMLTC0011b-JSON",
        "RMLTC0011b-XML",
        "RMLTC0012a-CSV",
        "RMLTC0012a-JSON",
        "RMLTC0012a-XML",
        "RMLTC0012b-CSV",
        "RMLTC0012b-JSON",
        "RMLTC0012b-XML",
        "RMLTC0013a-JSON",
        "RMLTC0015a-CSV",
        "RMLTC0015a-JSON",
        "RMLTC0015a-XML",
        "RMLTC0019a-CSV",
        "RMLTC0019a-JSON",
        "RMLTC0019a-XML",
        "RMLTC0020a-CSV",
        "RMLTC0020a-JSON",
        "RMLTC0020a-XML",
        "RMLTC0021a-CSV",
        "RMLTC0021a-JSON",
        "RMLTC0021a-XML",
        //"RMLTC0022a-CSV",
        //"RMLTC0022b-CSV",
        //"RMLTC0022c-CSV",
        "RMLTC0023a-XML",
        //"RMLTC0026a-JSON",
        "RMLTC0027-XML"
    })
    public void rmlTest(String folder) throws Exception {
        String m = new File(base + folder, "mapping.ttl").getAbsolutePath();

        // Parse the mapping file
        List<TriplesMap> triplesmaps = Parse.parseMappingFile(m);

        DatasetGraph actual = DatasetGraphFactory.create();

        String baseIRI = "http://example.com/base/";

        Generator generator = new Generator();
        
        try {
            generator.generate(triplesmaps, baseIRI, Quad.defaultGraphIRI, quad -> {
                actual.add(quad);
            });
        } catch(Exception e) {
            System.out.println(folder);
            Assertions.fail(e);
        }

        String o = new File(base + folder, "output.nq").getAbsolutePath();
        Model expected = RDFDataMgr.loadModel(o);
        
        boolean b = actual.getDefaultGraph().isIsomorphicWith(expected.getGraph());
                
        if(!b) {
            System.out.println(folder);
            System.out.println("Expected:");
            expected.write(System.out, "Turtle");
            System.out.println("---");
            System.out.println("Actual:");
            RDFDataMgr.write(System.out, actual, Lang.TRIG);
            System.out.println("===============");
        }
        
        assertTrue(b);
    }

}
