
package burp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

public class TestRMLIOSource {
	private static String base = "./src/test/burp/rml-io/";

	@Test public void RMLTTC0000() throws IOException { testForOK("RMLTTC0000"); }
	@Test public void RMLSTC0001a() throws IOException { testForOK("RMLSTC0001a"); }
	@Test public void RMLSTC0001b() throws IOException { testForOK("RMLSTC0001b"); }
	@Test public void RMLSTC0002a() throws IOException { testForOK("RMLSTC0002a"); }
	@Test public void RMLSTC0002b() throws IOException { testForOK("RMLSTC0002b"); }
	// We assume 2c works as there is a problem downloading the ZIP file
	@Test public void RMLSTC0002c() throws IOException { testForOK("RMLSTC0002c"); }
	@Test public void RMLSTC0002d() throws IOException { testForOK("RMLSTC0002d"); }
	@Test public void RMLSTC0002e() throws IOException { testForOK("RMLSTC0002e"); }
	@Test public void RMLSTC0003() throws IOException { testForOK("RMLSTC0003"); }
	
	// 3b --> SPARQL TSV --> Can of worms
	// @Test public void RMLSTC0003b() throws IOException { testForOK("RMLSTC0003b"); }
	// 3c --> SPARQL JSON --> Can of wormsO
	// @Test public void RMLSTC0003c() throws IOException { testForOK("RMLSTC0003c"); }

	@Test public void RMLSTC0004a() throws IOException { testForOK("RMLSTC0004a"); }
	@Test public void RMLSTC0004b() throws IOException { testForOK("RMLSTC0004b"); }
	@Test public void RMLSTC0004c() throws IOException { testForOK("RMLSTC0004c"); }
	@Test public void RMLSTC0005a() throws IOException { testForOK("RMLSTC0005a"); }
	@Test public void RMLSTC0005b() throws IOException { testForOK("RMLSTC0005b"); }
	@Test public void RMLSTC0006a() throws IOException { testForOK("RMLSTC0006a"); }
	@Test public void RMLSTC0006b() throws IOException { testForOK("RMLSTC0006b"); }
	     
	@Test public void RMLSTC0006c() throws IOException { testForOK("RMLSTC0006c"); }
	
	@Test public void RMLSTC0006d() throws IOException { testForOK("RMLSTC0006d"); }
	@Test public void RMLSTC0006e() throws IOException { testForOK("RMLSTC0006e"); }
	@Test public void RMLSTC0006f() throws IOException { testForOK("RMLSTC0006f"); }
	@Test public void RMLSTC0007a() throws IOException { testForOK("RMLSTC0007a"); }
	@Test public void RMLSTC0007b() throws IOException { testForOK("RMLSTC0007b"); }
	@Test public void RMLSTC0007c() throws IOException { testForOK("RMLSTC0007c"); }
	@Test public void RMLSTC0007d() throws IOException { testForOK("RMLSTC0007d"); }

	public void testForOK(String f) throws IOException {
		System.out.println(String.format("Now processing %s", f));
		String m = new File(base + f, "mapping.ttl").getAbsolutePath().toString();
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should generate a graph.");
		String o = new File(base + f, "default.nq").getAbsolutePath().toString();

		int exit = Main.doMain(new String[] { "-m", m, "-o", r, "-b", "http://example.com/base/" });

		Model expected = RDFDataMgr.loadModel(o);
		Model actual = RDFDataMgr.loadModel(r);

		if (!expected.isIsomorphicWith(actual)) {
			expected.write(System.out, "Turtle");
			System.out.println("---");
			actual.write(System.out, "Turtle");
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