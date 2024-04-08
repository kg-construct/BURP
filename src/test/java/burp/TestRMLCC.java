
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

public class TestRMLCC {
	private static String base = "./src/test/resources/rml-cc/";

	@Test public void RMLTCCC0001Alt() throws IOException { testForOK("RMLTC-CC-0001-Alt"); }
	@Test public void RMLTCCC0001Bag() throws IOException { testForOK("RMLTC-CC-0001-Bag"); }
	@Test public void RMLTCCC0001List() throws IOException { testForOK("RMLTC-CC-0001-List"); }
	@Test public void RMLTCCC0001Seq() throws IOException { testForOK("RMLTC-CC-0001-Seq"); }
	@Test public void RMLTCCC0002Bag() throws IOException { testForOK("RMLTC-CC-0002-Bag"); }
	@Test public void RMLTCCC0002List() throws IOException { testForOK("RMLTC-CC-0002-List"); }
	@Test public void RMLTCCC0003EB() throws IOException { testForOK("RMLTC-CC-0003-EB"); }
	@Test public void RMLTCCC0003EL() throws IOException { testForOK("RMLTC-CC-0003-EL"); }
	@Test public void RMLTCCC0003NEB() throws IOException { testForOK("RMLTC-CC-0003-NEB"); }
	@Test public void RMLTCCC0003NEL() throws IOException { testForOK("RMLTC-CC-0003-NEL"); }
	@Test public void RMLTCCC0003NELb() throws IOException { testForOK("RMLTC-CC-0003-NELb"); }
	@Test public void RMLTCCC0004SM1() throws IOException { testForOK("RMLTC-CC-0004-SM1"); }
	@Test public void RMLTCCC0004SM2() throws IOException { testForOK("RMLTC-CC-0004-SM2"); }
	@Test public void RMLTCCC0004SM3() throws IOException { testForOK("RMLTC-CC-0004-SM3"); }
	@Test public void RMLTCCC0004SM4() throws IOException { testForOK("RMLTC-CC-0004-SM4"); }
	@Test public void RMLTCCC0004SM5() throws IOException { testForOK("RMLTC-CC-0004-SM5"); }
	@Test public void RMLTCCC0005App1() throws IOException { testForOK("RMLTC-CC-0005-App1"); }
	@Test public void RMLTCCC0005App2() throws IOException { testForOK("RMLTC-CC-0005-App2"); }
	@Test public void RMLTCCC0005Car1() throws IOException { testForOK("RMLTC-CC-0005-Car1"); }
	@Test public void RMLTCCC0005Car2() throws IOException { testForOK("RMLTC-CC-0005-Car2"); }
	@Test public void RMLTCCC0006IT0() throws IOException { testForOK("RMLTC-CC-0006-IT0"); }
	@Test public void RMLTCCC0006IT1() throws IOException { testForOK("RMLTC-CC-0006-IT1"); }
	@Test public void RMLTCCC0006IT2() throws IOException { testForOK("RMLTC-CC-0006-IT2"); }
	@Test public void RMLTCCC0007NES() throws IOException { testForOK("RMLTC-CC-0007-NES"); }
	@Test public void RMLTCCC0008ROMa() throws IOException { testForOK("RMLTC-CC-0008-ROMa"); }
	@Test public void RMLTCCC0008ROMb() throws IOException { testForOK("RMLTC-CC-0008-ROMb"); }
	
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