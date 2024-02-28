package burp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

public class TestRMLCore {
	private static String base = "./src/test/burp/rml-core/";

	public static void main(String[] args) {
		try (Stream<Path> stream = Files.list(Paths.get(base))) {
			List<String> files = stream.filter(f -> Files.isDirectory(f) && f.getFileName().toString().contains("XML"))
					.map(Path::getFileName).map(Path::toString).collect(Collectors.toList());

			for (String f : files) {
				if (!new File(base + f, "output.nq").exists()) {
					String f1 = f.replace("-", "");
					System.out.println("@Test public void " + f1 + "() throws IOException { testForNotOK(\"" + f + "\"); }");
				}
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@Test public void RMLTC0000CSV() throws IOException { testForOK("RMLTC0000-CSV"); }
	@Test public void RMLTC0001aCSV() throws IOException { testForOK("RMLTC0001a-CSV"); }
	@Test public void RMLTC0001bCSV() throws IOException { testForOK("RMLTC0001b-CSV"); }
	@Test public void RMLTC0002aCSV() throws IOException { testForOK("RMLTC0002a-CSV"); }
	@Test public void RMLTC0002bCSV() throws IOException { testForOK("RMLTC0002b-CSV"); }
	@Test public void RMLTC0003cCSV() throws IOException { testForOK("RMLTC0003c-CSV"); }
	@Test public void RMLTC0004aCSV() throws IOException { testForOK("RMLTC0004a-CSV"); }
	@Test public void RMLTC0005aCSV() throws IOException { testForOK("RMLTC0005a-CSV"); }
	@Test public void RMLTC0006aCSV() throws IOException { testForOK("RMLTC0006a-CSV"); }
	@Test public void RMLTC0007aCSV() throws IOException { testForOK("RMLTC0007a-CSV"); }
	@Test public void RMLTC0007bCSV() throws IOException { testForOK("RMLTC0007b-CSV"); }
	@Test public void RMLTC0007cCSV() throws IOException { testForOK("RMLTC0007c-CSV"); }
	@Test public void RMLTC0007dCSV() throws IOException { testForOK("RMLTC0007d-CSV"); }
	@Test public void RMLTC0007eCSV() throws IOException { testForOK("RMLTC0007e-CSV"); }
	@Test public void RMLTC0007fCSV() throws IOException { testForOK("RMLTC0007f-CSV"); }
	@Test public void RMLTC0007gCSV() throws IOException { testForOK("RMLTC0007g-CSV"); }
	@Test public void RMLTC0008aCSV() throws IOException { testForOK("RMLTC0008a-CSV"); }
	@Test public void RMLTC0008bCSV() throws IOException { testForOK("RMLTC0008b-CSV"); }
	@Test public void RMLTC0008cCSV() throws IOException { testForOK("RMLTC0008c-CSV"); }
	@Test public void RMLTC0009aCSV() throws IOException { testForOK("RMLTC0009a-CSV"); }
	@Test public void RMLTC0009bCSV() throws IOException { testForOK("RMLTC0009b-CSV"); }
	@Test public void RMLTC0010aCSV() throws IOException { testForOK("RMLTC0010a-CSV"); }
	@Test public void RMLTC0010bCSV() throws IOException { testForOK("RMLTC0010b-CSV"); }
	@Test public void RMLTC0010cCSV() throws IOException { testForOK("RMLTC0010c-CSV"); }
	@Test public void RMLTC0011bCSV() throws IOException { testForOK("RMLTC0011b-CSV"); }
	@Test public void RMLTC0012aCSV() throws IOException { testForOK("RMLTC0012a-CSV"); }
	@Test public void RMLTC0012bCSV() throws IOException { testForOK("RMLTC0012b-CSV"); }
	@Test public void RMLTC0015aCSV() throws IOException { testForOK("RMLTC0015a-CSV"); }
	@Test public void RMLTC0019aCSV() throws IOException { testForOK("RMLTC0019a-CSV"); }
	@Test public void RMLTC0020aCSV() throws IOException { testForOK("RMLTC0020a-CSV"); }
	@Test public void RMLTC0021aCSV() throws IOException { testForOK("RMLTC0021a-CSV"); }
	@Test public void RMLTC0022aCSV() throws IOException { testForOK("RMLTC0022a-CSV"); }
	@Test public void RMLTC0022bCSV() throws IOException { testForOK("RMLTC0022b-CSV"); }
	@Test public void RMLTC0022cCSV() throws IOException { testForOK("RMLTC0022c-CSV"); }

	@Test public void RMLTC0002cCSV() throws IOException { testForNotOK("RMLTC0002c-CSV"); }
	@Test public void RMLTC0002eCSV() throws IOException { testForNotOK("RMLTC0002e-CSV"); }
	@Test public void RMLTC0004bCSV() throws IOException { testForNotOK("RMLTC0004b-CSV"); }
	@Test public void RMLTC0007hCSV() throws IOException { testForNotOK("RMLTC0007h-CSV"); }
	@Test public void RMLTC0012cCSV() throws IOException { testForNotOK("RMLTC0012c-CSV"); }
	@Test public void RMLTC0012dCSV() throws IOException { testForNotOK("RMLTC0012d-CSV"); }
	@Test public void RMLTC0015bCSV() throws IOException { testForNotOK("RMLTC0015b-CSV"); }
	@Test public void RMLTC0019bCSV() throws IOException { testForNotOK("RMLTC0019b-CSV"); }
	
	@Test public void RMLTC0000JSON() throws IOException { testForOK("RMLTC0000-JSON"); }
	@Test public void RMLTC0001aJSON() throws IOException { testForOK("RMLTC0001a-JSON"); }
	@Test public void RMLTC0001bJSON() throws IOException { testForOK("RMLTC0001b-JSON"); }
	@Test public void RMLTC0002aJSON() throws IOException { testForOK("RMLTC0002a-JSON"); }
	@Test public void RMLTC0002bJSON() throws IOException { testForOK("RMLTC0002b-JSON"); }
	// This test should be removed
	// @Test public void RMLTC0002cJSON() throws IOException { testForOK("RMLTC0002c-JSON"); }
	@Test public void RMLTC0003cJSON() throws IOException { testForOK("RMLTC0003c-JSON"); }
	@Test public void RMLTC0004aJSON() throws IOException { testForOK("RMLTC0004a-JSON"); }
	@Test public void RMLTC0005aJSON() throws IOException { testForOK("RMLTC0005a-JSON"); }
	@Test public void RMLTC0006aJSON() throws IOException { testForOK("RMLTC0006a-JSON"); }
	@Test public void RMLTC0007aJSON() throws IOException { testForOK("RMLTC0007a-JSON"); }
	@Test public void RMLTC0007bJSON() throws IOException { testForOK("RMLTC0007b-JSON"); }
	@Test public void RMLTC0007cJSON() throws IOException { testForOK("RMLTC0007c-JSON"); }
	@Test public void RMLTC0007dJSON() throws IOException { testForOK("RMLTC0007d-JSON"); }
	@Test public void RMLTC0007eJSON() throws IOException { testForOK("RMLTC0007e-JSON"); }
	@Test public void RMLTC0007fJSON() throws IOException { testForOK("RMLTC0007f-JSON"); }
	@Test public void RMLTC0007gJSON() throws IOException { testForOK("RMLTC0007g-JSON"); }
	@Test public void RMLTC0008aJSON() throws IOException { testForOK("RMLTC0008a-JSON"); }
	@Test public void RMLTC0008bJSON() throws IOException { testForOK("RMLTC0008b-JSON"); }
	@Test public void RMLTC0008cJSON() throws IOException { testForOK("RMLTC0008c-JSON"); }
	@Test public void RMLTC0009aJSON() throws IOException { testForOK("RMLTC0009a-JSON"); }
	@Test public void RMLTC0009bJSON() throws IOException { testForOK("RMLTC0009b-JSON"); }
	@Test public void RMLTC0010aJSON() throws IOException { testForOK("RMLTC0010a-JSON"); }
	@Test public void RMLTC0010bJSON() throws IOException { testForOK("RMLTC0010b-JSON"); }
	@Test public void RMLTC0010cJSON() throws IOException { testForOK("RMLTC0010c-JSON"); }
	@Test public void RMLTC0011bJSON() throws IOException { testForOK("RMLTC0011b-JSON"); }
	@Test public void RMLTC0012aJSON() throws IOException { testForOK("RMLTC0012a-JSON"); }
	@Test public void RMLTC0012bJSON() throws IOException { testForOK("RMLTC0012b-JSON"); }
	@Test public void RMLTC0013aJSON() throws IOException { testForOK("RMLTC0013a-JSON"); }
	@Test public void RMLTC0015aJSON() throws IOException { testForOK("RMLTC0015a-JSON"); }
	@Test public void RMLTC0019aJSON() throws IOException { testForOK("RMLTC0019a-JSON"); }
	@Test public void RMLTC0020aJSON() throws IOException { testForOK("RMLTC0020a-JSON"); }
	@Test public void RMLTC0021aJSON() throws IOException { testForOK("RMLTC0021a-JSON"); }
	
	@Test public void RMLTC0002eJSON() throws IOException { testForNotOK("RMLTC0002e-JSON"); }
	@Test public void RMLTC0002gJSON() throws IOException { testForNotOK("RMLTC0002g-JSON"); }
	@Test public void RMLTC0004bJSON() throws IOException { testForNotOK("RMLTC0004b-JSON"); }
	@Test public void RMLTC0007hJSON() throws IOException { testForNotOK("RMLTC0007h-JSON"); }
	@Test public void RMLTC0012cJSON() throws IOException { testForNotOK("RMLTC0012c-JSON"); }
	@Test public void RMLTC0012dJSON() throws IOException { testForNotOK("RMLTC0012d-JSON"); }
	@Test public void RMLTC0015bJSON() throws IOException { testForNotOK("RMLTC0015b-JSON"); }
	@Test public void RMLTC0019bJSON() throws IOException { testForNotOK("RMLTC0019b-JSON"); }
	
	@Test public void RMLTC0000XML() throws IOException { testForOK("RMLTC0000-XML"); }
	@Test public void RMLTC0001aXML() throws IOException { testForOK("RMLTC0001a-XML"); }
	@Test public void RMLTC0001bXML() throws IOException { testForOK("RMLTC0001b-XML"); }
	@Test public void RMLTC0002aXML() throws IOException { testForOK("RMLTC0002a-XML"); }
	@Test public void RMLTC0002bXML() throws IOException { testForOK("RMLTC0002b-XML"); }
	// This test should be removed
	//@Test public void RMLTC0002cXML() throws IOException { testForOK("RMLTC0002c-XML"); }
	@Test public void RMLTC0003cXML() throws IOException { testForOK("RMLTC0003c-XML"); }
	@Test public void RMLTC0004aXML() throws IOException { testForOK("RMLTC0004a-XML"); }
	@Test public void RMLTC0005aXML() throws IOException { testForOK("RMLTC0005a-XML"); }
	@Test public void RMLTC0006aXML() throws IOException { testForOK("RMLTC0006a-XML"); }
	@Test public void RMLTC0007aXML() throws IOException { testForOK("RMLTC0007a-XML"); }
	@Test public void RMLTC0007bXML() throws IOException { testForOK("RMLTC0007b-XML"); }
	@Test public void RMLTC0007cXML() throws IOException { testForOK("RMLTC0007c-XML"); }
	@Test public void RMLTC0007dXML() throws IOException { testForOK("RMLTC0007d-XML"); }
	@Test public void RMLTC0007eXML() throws IOException { testForOK("RMLTC0007e-XML"); }
	@Test public void RMLTC0007fXML() throws IOException { testForOK("RMLTC0007f-XML"); }
	@Test public void RMLTC0007gXML() throws IOException { testForOK("RMLTC0007g-XML"); }
	@Test public void RMLTC0008aXML() throws IOException { testForOK("RMLTC0008a-XML"); }
	@Test public void RMLTC0008bXML() throws IOException { testForOK("RMLTC0008b-XML"); }
	@Test public void RMLTC0008cXML() throws IOException { testForOK("RMLTC0008c-XML"); }
	@Test public void RMLTC0009aXML() throws IOException { testForOK("RMLTC0009a-XML"); }
	@Test public void RMLTC0009bXML() throws IOException { testForOK("RMLTC0009b-XML"); }
	@Test public void RMLTC0010bXML() throws IOException { testForOK("RMLTC0010b-XML"); }
	@Test public void RMLTC0010cXML() throws IOException { testForOK("RMLTC0010c-XML"); }
	@Test public void RMLTC0011bXML() throws IOException { testForOK("RMLTC0011b-XML"); }
	@Test public void RMLTC0012aXML() throws IOException { testForOK("RMLTC0012a-XML"); }
	@Test public void RMLTC0012bXML() throws IOException { testForOK("RMLTC0012b-XML"); }
	@Test public void RMLTC0015aXML() throws IOException { testForOK("RMLTC0015a-XML"); }
	@Test public void RMLTC0019aXML() throws IOException { testForOK("RMLTC0019a-XML"); }
	@Test public void RMLTC0020aXML() throws IOException { testForOK("RMLTC0020a-XML"); }
	@Test public void RMLTC0021aXML() throws IOException { testForOK("RMLTC0021a-XML"); }
	@Test public void RMLTC0023aXML() throws IOException { testForOK("RMLTC0023a-XML"); }
	
	@Test public void RMLTC0002eXML() throws IOException { testForNotOK("RMLTC0002e-XML"); }
	@Test public void RMLTC0004bXML() throws IOException { testForNotOK("RMLTC0004b-XML"); }
	@Test public void RMLTC0007hXML() throws IOException { testForNotOK("RMLTC0007h-XML"); }
	@Test public void RMLTC0012cXML() throws IOException { testForNotOK("RMLTC0012c-XML"); }
	@Test public void RMLTC0012dXML() throws IOException { testForNotOK("RMLTC0012d-XML"); }
	@Test public void RMLTC0015bXML() throws IOException { testForNotOK("RMLTC0015b-XML"); }
	@Test public void RMLTC0019bXML() throws IOException { testForNotOK("RMLTC0019b-XML"); }
	
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
			System.out.println();
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