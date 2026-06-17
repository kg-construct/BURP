
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

public class TestRMLFNMLGrellFunctions {
	private static String base = "./src/test/resources/rml-fnml-grel/";

	@Test public void RMLFNOTC0001() throws IOException { testForOK("string-length"); }
	@Test public void RMLFNOTC0002() throws IOException { testForOK("string-touppercase"); }
	@Test public void RMLFNOTC0003() throws IOException { testForOK("string-tolowercase"); }
	@Test public void RMLFNOTC0004() throws IOException { testForOK("string-totitlecase-a"); }
	@Test public void RMLFNOTC0005() throws IOException { testForOK("string-totitlecase-b"); }
	@Test public void RMLFNOTC0006() throws IOException { testForOK("string-replace"); }
	@Test public void RMLFNOTC0007() throws IOException { testForOK("string-startswith-true"); }
	@Test public void RMLFNOTC0008() throws IOException { testForOK("string-startswith-false"); }
	@Test public void RMLFNOTC0009() throws IOException { testForOK("string-endswith-true"); }
	@Test public void RMLFNOTC0010() throws IOException { testForOK("string-endswith-false"); }
	@Test public void RMLFNOTC0011() throws IOException { testForOK("string-trim"); }
	@Test public void RMLFNOTC0012() throws IOException { testForOK("string-strip"); }
	@Test public void RMLFNOTC0013() throws IOException { testForOK("string-chomp-a"); }
	@Test public void RMLFNOTC0014() throws IOException { testForOK("string-chomp-b"); }
	@Test public void RMLFNOTC0015() throws IOException { testForOK("math-abs"); }
	@Test public void RMLFNOTC0016() throws IOException { testForOK("math-ceil"); }
	@Test public void RMLFNOTC0017() throws IOException { testForOK("math-floor"); }
	@Test public void RMLFNOTC0018() throws IOException { testForOK("string-contains-s-true"); }
	@Test public void RMLFNOTC0019() throws IOException { testForOK("string-contains-s-false"); }
	@Test public void RMLFNOTC0020() throws IOException { testForOK("string-contains-p-true"); }
	@Test public void RMLFNOTC0021() throws IOException { testForOK("string-contains-p-false"); }
	@Test public void RMLFNOTC0022() throws IOException { testForOK("string-substring-a"); }
	@Test public void RMLFNOTC0023() throws IOException { testForOK("string-substring-b"); }
	@Test public void RMLFNOTC0024() throws IOException { testForOK("string-substring-c"); }
	@Test public void RMLFNOTC0025() throws IOException { testForOK("string-get-a"); }
	@Test public void RMLFNOTC0026() throws IOException { testForOK("string-get-b"); }
	@Test public void RMLFNOTC0027() throws IOException { testForOK("string-get-c"); }
	@Test public void RMLFNOTC0028() throws IOException { testForOK("boolean-and-true"); }
	@Test public void RMLFNOTC0029() throws IOException { testForOK("boolean-and-false"); }
	@Test public void RMLFNOTC0030() throws IOException { testForOK("boolean-or-true"); }
	@Test public void RMLFNOTC0031() throws IOException { testForOK("boolean-or-false"); }
	@Test public void RMLFNOTC0032() throws IOException { testForOK("boolean-xor-false"); }
	@Test public void RMLFNOTC0033() throws IOException { testForOK("boolean-xor-false"); }
	@Test public void RMLFNOTC0034() throws IOException { testForOK("boolean-not-false"); }
	@Test public void RMLFNOTC0035() throws IOException { testForOK("boolean-not-false"); }

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