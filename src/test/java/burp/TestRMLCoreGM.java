package burp;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRMLCoreGM {
	private static String base = "./src/test/resources/rml-core-gm/";

	// all in default graph
	@Test public void RMLTC0001() throws IOException { testForOK("RMLTC0001"); }

	// all in default graph with rml:defaultGraph
	@Test public void RMLTC0002() throws IOException { testForOK("RMLTC0002"); }

	// graphMap in subjectMap
	@Test public void RMLTC0003() throws IOException { testForOK("RMLTC0003"); }

	// graphMap in subjectMap, default graph in POM
	@Test public void RMLTC0004() throws IOException { testForOK("RMLTC0004"); }

	// graphMap in subjectMap, default graph in POM
	@Test public void RMLTC0005() throws IOException { testForOK("RMLTC0005"); }

	public void testForOK(String f) throws IOException {
		System.out.println(String.format("Now processing %s", f));
		String m = new File(base + f, "mapping.ttl").getAbsolutePath().toString();
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should generate a graph.");
		String o = new File(base + f, "output.nq").getAbsolutePath().toString();

		int exit = Main.doMain(new String[] { "-m", m, "-o", r, "-b", "http://example.com/base/" });

		Model expected = RDFDataMgr.loadModel(o);
		Model actual = RDFDataMgr.loadModel(r);

		if (!expected.isIsomorphicWith(actual)) {
			expected.write(System.out, "NQ");
			System.out.println("----");
			actual.write(System.out, "NQ");
		}

		assertEquals(0, exit);

		System.out.println(expected.isIsomorphicWith(actual) ? "OK" : "NOK");

		assertTrue(expected.isIsomorphicWith(actual));
	}

	public void testForNotOK(String f) throws IOException {
		System.out.println(String.format("Now processing %s", f));
		String m = new File(base + f, "mapping.ttl").getAbsolutePath().toString();
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should NOT generate a graph.");
		int exit = Main.doMain(new String[] { "-m", m, "-o", r });
		System.out.println(Files.size(Paths.get(r)) == 0 ? "OK" : "NOK");
		Model actual = RDFDataMgr.loadModel(r);
		actual.write(System.out, "NQ");

		assertTrue(exit > 0);
		assertTrue(Files.size(Paths.get(r)) == 0);

		System.out.println();
	}

}