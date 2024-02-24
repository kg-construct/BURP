package burp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;


@Testcontainers
public class TestRMLCoreMySQL {

	private static String base = "./src/test/burp/rml-core/";

	public static void main(String[] args) {
		try (Stream<Path> stream = Files.list(Paths.get(base))) {
			List<String> files = stream.filter(f -> Files.isDirectory(f) && f.getFileName().toString().contains("MySQL"))
					.map(Path::getFileName).map(Path::toString).collect(Collectors.toList());

			for (String f : files) {
				if (!new File(base + f, "output.nq").exists()) {
					String f1 = f.replace("-", "");
					System.out.println("@Test public void " + f1 + "() throws Exception { testForNotOK(\"" + f + "\"); }");
				}
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			throw new RuntimeException(e);
		}
	}
	
	@Test public void RMLTC0000MySQL() throws Exception { testForOK("RMLTC0000-MySQL"); }
	@Test public void RMLTC0001aMySQL() throws Exception { testForOK("RMLTC0001a-MySQL"); }
	@Test public void RMLTC0001bMySQL() throws Exception { testForOK("RMLTC0001b-MySQL"); }
	@Test public void RMLTC0002aMySQL() throws Exception { testForOK("RMLTC0002a-MySQL"); }
	@Test public void RMLTC0002bMySQL() throws Exception { testForOK("RMLTC0002b-MySQL"); }
	@Test public void RMLTC0002cMySQL() throws Exception { testForOK("RMLTC0002c-MySQL"); }
	@Test public void RMLTC0002dMySQL() throws Exception { testForOK("RMLTC0002d-MySQL"); }
	@Test public void RMLTC0002hMySQL() throws Exception { testForOK("RMLTC0002h-MySQL"); }
	@Test public void RMLTC0002iMySQL() throws Exception { testForOK("RMLTC0002i-MySQL"); }
	@Test public void RMLTC0002jMySQL() throws Exception { testForOK("RMLTC0002j-MySQL"); }
	@Test public void RMLTC0003aMySQL() throws Exception { testForOK("RMLTC0003a-MySQL"); }
	@Test public void RMLTC0003bMySQL() throws Exception { testForOK("RMLTC0003b-MySQL"); }
	@Test public void RMLTC0003cMySQL() throws Exception { testForOK("RMLTC0003c-MySQL"); }
	@Test public void RMLTC0004aMySQL() throws Exception { testForOK("RMLTC0004a-MySQL"); }
	@Test public void RMLTC0005aMySQL() throws Exception { testForOK("RMLTC0005a-MySQL"); }
	@Test public void RMLTC0005bMySQL() throws Exception { testForOK("RMLTC0005b-MySQL"); }
	@Test public void RMLTC0006aMySQL() throws Exception { testForOK("RMLTC0006a-MySQL"); }
	@Test public void RMLTC0007aMySQL() throws Exception { testForOK("RMLTC0007a-MySQL"); }
	@Test public void RMLTC0007bMySQL() throws Exception { testForOK("RMLTC0007b-MySQL"); }
	@Test public void RMLTC0007cMySQL() throws Exception { testForOK("RMLTC0007c-MySQL"); }
	@Test public void RMLTC0007dMySQL() throws Exception { testForOK("RMLTC0007d-MySQL"); }
	@Test public void RMLTC0007eMySQL() throws Exception { testForOK("RMLTC0007e-MySQL"); }
	@Test public void RMLTC0007fMySQL() throws Exception { testForOK("RMLTC0007f-MySQL"); }
	@Test public void RMLTC0007gMySQL() throws Exception { testForOK("RMLTC0007g-MySQL"); }
	@Test public void RMLTC0008aMySQL() throws Exception { testForOK("RMLTC0008a-MySQL"); }
	@Test public void RMLTC0008bMySQL() throws Exception { testForOK("RMLTC0008b-MySQL"); }
	@Test public void RMLTC0008cMySQL() throws Exception { testForOK("RMLTC0008c-MySQL"); }
	@Test public void RMLTC0009aMySQL() throws Exception { testForOK("RMLTC0009a-MySQL"); }
	@Test public void RMLTC0009bMySQL() throws Exception { testForOK("RMLTC0009b-MySQL"); }
	@Test public void RMLTC0009cMySQL() throws Exception { testForOK("RMLTC0009c-MySQL"); }
	@Test public void RMLTC0009dMySQL() throws Exception { testForOK("RMLTC0009d-MySQL"); }
	@Test public void RMLTC0010aMySQL() throws Exception { testForOK("RMLTC0010a-MySQL"); }
	@Test public void RMLTC0010bMySQL() throws Exception { testForOK("RMLTC0010b-MySQL"); }
	@Test public void RMLTC0010cMySQL() throws Exception { testForOK("RMLTC0010c-MySQL"); }
	@Test public void RMLTC0011aMySQL() throws Exception { testForOK("RMLTC0011a-MySQL"); }
	@Test public void RMLTC0011bMySQL() throws Exception { testForOK("RMLTC0011b-MySQL"); }
	@Test public void RMLTC0012aMySQL() throws Exception { testForOK("RMLTC0012a-MySQL"); }
	@Test public void RMLTC0012bMySQL() throws Exception { testForOK("RMLTC0012b-MySQL"); }
	@Test public void RMLTC0012eMySQL() throws Exception { testForOK("RMLTC0012e-MySQL"); }
	@Test public void RMLTC0013aMySQL() throws Exception { testForOK("RMLTC0013a-MySQL"); }
	@Test public void RMLTC0014dMySQL() throws Exception { testForOK("RMLTC0014d-MySQL"); }
	@Test public void RMLTC0015aMySQL() throws Exception { testForOK("RMLTC0015a-MySQL"); }
	@Test public void RMLTC0016aMySQL() throws Exception { testForOK("RMLTC0016a-MySQL"); }
	@Test public void RMLTC0016bMySQL() throws Exception { testForOK("RMLTC0016b-MySQL"); }
	@Test public void RMLTC0016cMySQL() throws Exception { testForOK("RMLTC0016c-MySQL"); }
	@Test public void RMLTC0016dMySQL() throws Exception { testForOK("RMLTC0016d-MySQL"); }
	@Test public void RMLTC0016eMySQL() throws Exception { testForOK("RMLTC0016e-MySQL"); }
	@Test public void RMLTC0018aMySQL() throws Exception { testForOK("RMLTC0018a-MySQL"); }
	@Test public void RMLTC0019aMySQL() throws Exception { testForOK("RMLTC0019a-MySQL"); }
	@Test public void RMLTC0020aMySQL() throws Exception { testForOK("RMLTC0020a-MySQL"); }
	@Test public void RMLTC0021aMySQL() throws Exception { testForOK("RMLTC0021a-MySQL"); }

	@Test public void RMLTC0002eMySQL() throws Exception { testForNotOK("RMLTC0002e-MySQL"); }
	@Test public void RMLTC0002fMySQL() throws Exception { testForNotOK("RMLTC0002f-MySQL"); }
	@Test public void RMLTC0002gMySQL() throws Exception { testForNotOK("RMLTC0002g-MySQL"); }
	@Test public void RMLTC0004bMySQL() throws Exception { testForNotOK("RMLTC0004b-MySQL"); }
	@Test public void RMLTC0007hMySQL() throws Exception { testForNotOK("RMLTC0007h-MySQL"); }
	@Test public void RMLTC0012cMySQL() throws Exception { testForNotOK("RMLTC0012c-MySQL"); }
	@Test public void RMLTC0012dMySQL() throws Exception { testForNotOK("RMLTC0012d-MySQL"); }
	@Test public void RMLTC0015bMySQL() throws Exception { testForNotOK("RMLTC0015b-MySQL"); }
	@Test public void RMLTC0019bMySQL() throws Exception { testForNotOK("RMLTC0019b-MySQL"); }
	
	@SuppressWarnings({ "resource", "deprecation" })
	public void testForOK(String f) throws Exception {
		System.out.println(String.format("Now processing %s", f));
    	
		System.out.println("Loading the database");
    	MySQLContainer<?> MY_SQL_CONTAINER = new MySQLContainer<>("mysql:latest").withEnv("MYSQL_ROOT_HOST", "%");
		MY_SQL_CONTAINER.start();
		String jdbcurl = MY_SQL_CONTAINER.getJdbcUrl();
    	String ddl = FileUtils.readFileToString(new File(base + f, "resource.sql"));
    	Statement statement = MY_SQL_CONTAINER.createConnection("?allowMultiQueries=true").createStatement();
    	statement.execute(ddl);
    	System.out.println("Database loaded");
    	
    	String ms = FileUtils.readFileToString(new File(base + f, "mapping.ttl"));
    	ms = ms.replace("CONNECTIONDSN", jdbcurl);
    	ms = ms.replace("d2rq:password \"\"", "d2rq:password \"test\"");
    	File m2 = new File(base + f, "mapping-new.ttl");
        BufferedWriter writer = new BufferedWriter(new FileWriter(m2));
        writer.write(ms);
        writer.close();
    	
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should generate a graph.");
		String o = new File(base + f, "output.nq").getAbsolutePath().toString();

		int exit = Main.doMain(new String[] { "-m", m2.getAbsolutePath(), "-o", r, "-b", "http://example.com/base/" });

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
		
    	m2.delete();    	
		MY_SQL_CONTAINER.stop();
		
		System.out.println();
	}

	@SuppressWarnings({ "resource", "deprecation" })
	public void testForNotOK(String f) throws Exception {
		System.out.println(String.format("Now processing %s", f));
		
    	System.out.println("Loading the database");
    	MySQLContainer<?> MY_SQL_CONTAINER = new MySQLContainer<>("mysql:latest").withEnv("MYSQL_ROOT_HOST", "%").withEnv("MYSQL_ROOT_HOST", "%@%");
		MY_SQL_CONTAINER.start();
		String jdbcurl = MY_SQL_CONTAINER.getJdbcUrl();
    	String ddl = FileUtils.readFileToString(new File(base + f, "resource.sql"));
    	Statement statement = MY_SQL_CONTAINER.createConnection("?allowMultiQueries=true").createStatement();
    	statement.execute(ddl);
    	System.out.println("Database loaded");
    	
    	String ms = FileUtils.readFileToString(new File(base + f, "mapping.ttl"));
    	ms = ms.replace("CONNECTIONDSN", jdbcurl);
    	ms = ms.replace("d2rq:password \"\"", "d2rq:password \"test\"");
    	File m2 = new File(base + f, "mapping-new.ttl");
        BufferedWriter writer = new BufferedWriter(new FileWriter(m2));
        writer.write(ms);
        writer.close();
		
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should NOT generate a graph.");
		int exit = Main.doMain(new String[] { "-m", m2.getAbsolutePath(), "-o", r });
		System.out.println(Files.size(Paths.get(r)) == 0 ? "OK" : "NOK");
		Model actual = RDFDataMgr.loadModel(r);
		actual.write(System.out, "NQ");

		assertTrue(exit > 0);
		assertTrue(Files.size(Paths.get(r)) == 0);

    	m2.delete();    	
		MY_SQL_CONTAINER.stop();
		
		System.out.println();
	}
	
	

}
