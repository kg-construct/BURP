
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

public class TestRMLFNML {
	private static String base = "./src/test/resources/rml-fnml/";

	// UUID test should be removed from the test cases
	// @Test public void RMLFNOTC0000CSV() throws IOException { testForOK("RMLFNOTC0000-CSV"); }
	@Test public void RMLFNOTC0000CSVb() throws IOException { testForOK("RMLFNOTC0000b-CSV"); }
	@Test public void RMLFNOTC0001CSV() throws IOException { testForOK("RMLFNOTC0001-CSV"); }
	@Test public void RMLFNOTC0002CSV() throws IOException { testForNotOK("RMLFNOTC0002-CSV"); }
	@Test public void RMLFNOTC0003CSV() throws IOException { testForOK("RMLFNOTC0003-CSV"); }
	@Test public void RMLFNOTC0004CSV() throws IOException { testForOK("RMLFNOTC0004-CSV"); }
	@Test public void RMLFNOTC0004CSVb() throws IOException { testForOK("RMLFNOTC0004b-CSV"); }
	@Test public void RMLFNOTC0005CSV() throws IOException { testForOK("RMLFNOTC0005-CSV"); }
	@Test public void RMLFNOTC0005CSVb() throws IOException { testForOK("RMLFNOTC0005b-CSV"); }
	@Test public void RMLFNOTC0006CSV() throws IOException { testForOK("RMLFNOTC0006-CSV"); }
	@Test public void RMLFNOTC0006CSVb() throws IOException { testForOK("RMLFNOTC0006b-CSV"); }
	@Test public void RMLFNOTC0008CSV() throws IOException { testForOK("RMLFNOTC0008-CSV"); }
	@Test public void RMLFNOTC0009CSV() throws IOException { testForOK("RMLFNOTC0009-CSV"); }
	@Test public void RMLFNOTC0010CSV() throws IOException { testForOK("RMLFNOTC0010-CSV"); }


	
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