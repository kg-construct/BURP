package burp;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RML-IO gives the compression of a target to the target (rml:Target), as the test cases
 * RMLTTC0006b-e do since kg-construct/rml-io 0cb26a2; it is still read on the logical target.
 */
public class TargetCompressionTest {

    private static final String MAPPING = String.join("\n",
            "@prefix rml: <http://w3id.org/rml/> .",
            "@prefix formats: <http://www.w3.org/ns/formats/> .",
            "<http://example.com/base/TM> a rml:TriplesMap ;",
            "  rml:logicalSource [ rml:source [ a rml:RelativePathSource ; rml:root rml:MappingDirectory ; rml:path \"people.csv\" ] ;",
            "      rml:referenceFormulation rml:CSV ] ;",
            "  rml:subjectMap [ rml:template \"http://example.org/{id}\" ; rml:logicalTarget <http://example.com/base/Dump> ] ;",
            "  rml:predicateObjectMap [ rml:predicate <http://xmlns.com/foaf/0.1/name> ; rml:objectMap [ rml:reference \"name\" ] ] .",
            "<http://example.com/base/Dump> a rml:LogicalTarget ;",
            "  rml:target [ a rml:Target, rml:FilePath ; rml:root rml:CurrentWorkingDirectory ; rml:path \"./dump1.nq.gz\" ;",
            "      rml:compression rml:gzip ] ;",
            "  rml:serialization formats:N-Quads .");

    @Test
    public void testCompressionOnTarget() throws Exception {
        Path dir = Files.createTempDirectory("burp-target");
        Files.writeString(dir.resolve("people.csv"), "id,name\n0,Monica\n", StandardCharsets.UTF_8);
        Path mapping = dir.resolve("mapping.ttl");
        Files.writeString(mapping, MAPPING, StandardCharsets.UTF_8);
        Path output = dir.resolve("default.nq");
        Main.doMain(new String[]{"-m", mapping.toString(), "-o", output.toString()}, dir);
        try (InputStream in = new GZIPInputStream(Files.newInputStream(dir.resolve("dump1.nq.gz")))) {
            String nquads = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(nquads.contains("\"Monica\""), nquads);
        }
    }
}
