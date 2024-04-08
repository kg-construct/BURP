package burp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;


@Testcontainers
public class TestRMLCorePostgreSQL {

	private static String base = "./src/test/resources/rml-core/";
	
	@Test public void RMLTC0000PostgreSQL() throws Exception { testForOK("RMLTC0000-PostgreSQL"); }
	@Test public void RMLTC0001aPostgreSQL() throws Exception { testForOK("RMLTC0001a-PostgreSQL"); }
	@Test public void RMLTC0001bPostgreSQL() throws Exception { testForOK("RMLTC0001b-PostgreSQL"); }
	@Test public void RMLTC0002aPostgreSQL() throws Exception { testForOK("RMLTC0002a-PostgreSQL"); }
	@Test public void RMLTC0002bPostgreSQL() throws Exception { testForOK("RMLTC0002b-PostgreSQL"); }
	@Test public void RMLTC0002dPostgreSQL() throws Exception { testForOK("RMLTC0002d-PostgreSQL"); }
	@Test public void RMLTC0003bPostgreSQL() throws Exception { testForOK("RMLTC0003b-PostgreSQL"); }
	@Test public void RMLTC0003cPostgreSQL() throws Exception { testForOK("RMLTC0003c-PostgreSQL"); }
	@Test public void RMLTC0004aPostgreSQL() throws Exception { testForOK("RMLTC0004a-PostgreSQL"); }
	@Test public void RMLTC0005aPostgreSQL() throws Exception { testForOK("RMLTC0005a-PostgreSQL"); }
	@Test public void RMLTC0005bPostgreSQL() throws Exception { testForOK("RMLTC0005b-PostgreSQL"); }
	@Test public void RMLTC0006aPostgreSQL() throws Exception { testForOK("RMLTC0006a-PostgreSQL"); }
	@Test public void RMLTC0007aPostgreSQL() throws Exception { testForOK("RMLTC0007a-PostgreSQL"); }
	@Test public void RMLTC0007bPostgreSQL() throws Exception { testForOK("RMLTC0007b-PostgreSQL"); }
	@Test public void RMLTC0007cPostgreSQL() throws Exception { testForOK("RMLTC0007c-PostgreSQL"); }
	@Test public void RMLTC0007dPostgreSQL() throws Exception { testForOK("RMLTC0007d-PostgreSQL"); }
	@Test public void RMLTC0007ePostgreSQL() throws Exception { testForOK("RMLTC0007e-PostgreSQL"); }
	@Test public void RMLTC0007fPostgreSQL() throws Exception { testForOK("RMLTC0007f-PostgreSQL"); }
	@Test public void RMLTC0007gPostgreSQL() throws Exception { testForOK("RMLTC0007g-PostgreSQL"); }
	@Test public void RMLTC0008aPostgreSQL() throws Exception { testForOK("RMLTC0008a-PostgreSQL"); }
	@Test public void RMLTC0008bPostgreSQL() throws Exception { testForOK("RMLTC0008b-PostgreSQL"); }
	@Test public void RMLTC0008cPostgreSQL() throws Exception { testForOK("RMLTC0008c-PostgreSQL"); }
	@Test public void RMLTC0009aPostgreSQL() throws Exception { testForOK("RMLTC0009a-PostgreSQL"); }
	@Test public void RMLTC0009bPostgreSQL() throws Exception { testForOK("RMLTC0009b-PostgreSQL"); }
	@Test public void RMLTC0009cPostgreSQL() throws Exception { testForOK("RMLTC0009c-PostgreSQL"); }
	@Test public void RMLTC0009dPostgreSQL() throws Exception { testForOK("RMLTC0009d-PostgreSQL"); }
	@Test public void RMLTC0010aPostgreSQL() throws Exception { testForOK("RMLTC0010a-PostgreSQL"); }
	@Test public void RMLTC0010bPostgreSQL() throws Exception { testForOK("RMLTC0010b-PostgreSQL"); }
	@Test public void RMLTC0010cPostgreSQL() throws Exception { testForOK("RMLTC0010c-PostgreSQL"); }
	@Test public void RMLTC0011aPostgreSQL() throws Exception { testForOK("RMLTC0011a-PostgreSQL"); }
	@Test public void RMLTC0011bPostgreSQL() throws Exception { testForOK("RMLTC0011b-PostgreSQL"); }
	@Test public void RMLTC0012aPostgreSQL() throws Exception { testForOK("RMLTC0012a-PostgreSQL"); }
	@Test public void RMLTC0012bPostgreSQL() throws Exception { testForOK("RMLTC0012b-PostgreSQL"); }
	@Test public void RMLTC0012ePostgreSQL() throws Exception { testForOK("RMLTC0012e-PostgreSQL"); }
	@Test public void RMLTC0013aPostgreSQL() throws Exception { testForOK("RMLTC0013a-PostgreSQL"); }
	@Test public void RMLTC0014dPostgreSQL() throws Exception { testForOK("RMLTC0014d-PostgreSQL"); }
	@Test public void RMLTC0015aPostgreSQL() throws Exception { testForOK("RMLTC0015a-PostgreSQL"); }
	@Test public void RMLTC0016aPostgreSQL() throws Exception { testForOK("RMLTC0016a-PostgreSQL"); }
	@Test public void RMLTC0016bPostgreSQL() throws Exception { testForOK("RMLTC0016b-PostgreSQL"); }
	@Test public void RMLTC0016cPostgreSQL() throws Exception { testForOK("RMLTC0016c-PostgreSQL"); }
	@Test public void RMLTC0016dPostgreSQL() throws Exception { testForOK("RMLTC0016d-PostgreSQL"); }
	@Test public void RMLTC0016ePostgreSQL() throws Exception { testForOK("RMLTC0016e-PostgreSQL"); }
	@Test public void RMLTC0018aPostgreSQL() throws Exception { testForOK("RMLTC0018a-PostgreSQL"); }
	@Test public void RMLTC0019aPostgreSQL() throws Exception { testForOK("RMLTC0019a-PostgreSQL"); }
	@Test public void RMLTC0020aPostgreSQL() throws Exception { testForOK("RMLTC0020a-PostgreSQL"); }
	@Test public void RMLTC0021aPostgreSQL() throws Exception { testForOK("RMLTC0021a-PostgreSQL"); }

	@Test public void RMLTC0002cPostgreSQL() throws Exception { testForNotOK("RMLTC0002c-PostgreSQL"); }
	@Test public void RMLTC0002ePostgreSQL() throws Exception { testForNotOK("RMLTC0002e-PostgreSQL"); }
	@Test public void RMLTC0002fPostgreSQL() throws Exception { testForNotOK("RMLTC0002f-PostgreSQL"); }
	@Test public void RMLTC0002gPostgreSQL() throws Exception { testForNotOK("RMLTC0002g-PostgreSQL"); }
	@Test public void RMLTC0002hPostgreSQL() throws Exception { testForNotOK("RMLTC0002h-PostgreSQL"); }
	@Test public void RMLTC0002iPostgreSQL() throws Exception { testForNotOK("RMLTC0002i-PostgreSQL"); }
	@Test public void RMLTC0002jPostgreSQL() throws Exception { testForNotOK("RMLTC0002j-PostgreSQL"); }
	@Test public void RMLTC0003aPostgreSQL() throws Exception { testForNotOK("RMLTC0003a-PostgreSQL"); }
	@Test public void RMLTC0004bPostgreSQL() throws Exception { testForNotOK("RMLTC0004b-PostgreSQL"); }
	@Test public void RMLTC0007hPostgreSQL() throws Exception { testForNotOK("RMLTC0007h-PostgreSQL"); }
	@Test public void RMLTC0012cPostgreSQL() throws Exception { testForNotOK("RMLTC0012c-PostgreSQL"); }
	@Test public void RMLTC0012dPostgreSQL() throws Exception { testForNotOK("RMLTC0012d-PostgreSQL"); }
	@Test public void RMLTC0015bPostgreSQL() throws Exception { testForNotOK("RMLTC0015b-PostgreSQL"); }
	@Test public void RMLTC0019bPostgreSQL() throws Exception { testForNotOK("RMLTC0019b-PostgreSQL"); }
	
	@SuppressWarnings({ "resource", "deprecation" })
	public void testForOK(String f) throws Exception {
		System.out.println(String.format("Now processing %s", f));
		
		System.out.println("Loading the database");
        PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:latest")
                .withUsername("postgres")
                .withPassword("test");
        CONTAINER.start();
		String jdbcurl = CONTAINER.getJdbcUrl();
    	String ddl = FileUtils.readFileToString(new File(base + f, "resource.sql"));
    	Statement statement = CONTAINER.createConnection("?allowMultiQueries=true").createStatement();
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
    	CONTAINER.stop();
		
		System.out.println();
	}

	@SuppressWarnings({ "resource", "deprecation" })
	public void testForNotOK(String f) throws Exception {
		System.out.println(String.format("Now processing %s", f));
		
    	System.out.println("Loading the database");
        PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:latest")
                .withUsername("postgres")
                .withPassword("test");    	CONTAINER.start();
		String jdbcurl = CONTAINER.getJdbcUrl();
    	String ddl = FileUtils.readFileToString(new File(base + f, "resource.sql"));
    	Statement statement = CONTAINER.createConnection("?allowMultiQueries=true").createStatement();
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
    	CONTAINER.stop();
		
		System.out.println();
	}

}
