package burp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer.NoDriverFoundException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class TestRMLCoreMySQL {

	private static String base = "./src/test/burp/rml-core/";
	
	@Before
	public void prepareDatabase() throws NoDriverFoundException, SQLException {
		Statement statement = MY_SQL_CONTAINER.createConnection("").createStatement();
        statement.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY '';");
	}

	@Test
	public void testMySQL() {
    	try (Stream<Path> stream = Files.list(Paths.get(base))) {
            List<String> files = stream
              .filter(f -> Files.isDirectory(f) && f.getFileName().toString().contains("MySQL"))
              .map(Path::getFileName)
              .map(Path::toString)
              .collect(Collectors.toList());
              
            for(String f : files) {
            	System.out.println(String.format("Now processing %s", f));
            	
            	System.out.println("Loading the database");
            	
            	MySQLContainer<?> MY_SQL_CONTAINER = new MySQLContainer<>("mysql:latest").withEnv("MYSQL_ROOT_HOST", "%");
        		MY_SQL_CONTAINER.start();
        		
            	String ddl = FileUtils.readFileToString(new File(base + f, "resource.sql"));
            	Statement statement = MY_SQL_CONTAINER.createConnection("?allowMultiQueries=true").createStatement();
            	System.err.println(ddl);
                statement.execute(ddl);
            	System.out.println("Database loaded");

            	String m = new File(base + f, "mapping.ttl").getAbsolutePath().toString();
            	
            	String r = Files.createTempFile(null, ".nq").toString();
            	System.out.println(String.format("Writing output to %s", r));
            	
            	if(new File(base + f, "output.nq").exists()) {
            		System.out.println("This test should generate a graph.");
//                	String o = new File(base + f, "output.nq").getAbsolutePath().toString();
//                	
//
//            		Main.doMain(new String[] { "-m", m, "-o", r, "-b", "http://example.com/base/" });
//
//            		Model expected = RDFDataMgr.loadModel(o);
//            		Model actual = RDFDataMgr.loadModel(r);
//            		
//            		if(!expected.isIsomorphicWith(actual)) {
//            			expected.write(System.out, "NQ");
//            			System.out.println();
//            			actual.write(System.out, "NQ");
//            		}
//            		
//            		System.out.println(expected.isIsomorphicWith(actual) ? "OK" : "NOK");
//            		
//            		assertTrue(expected.isIsomorphicWith(actual));            		
            	} else {
            		System.out.println("This test should NOT generate a graph.");
            		
//            		Main.doMain(new String[] { "-m", m, "-o", r });            		
//
//            		System.out.println(Files.size(Paths.get(r)) == 0 ? "OK" : "NOK");
//            		
//                	Model actual = RDFDataMgr.loadModel(r);
//            		
//            		actual.write(System.out, "NQ");
//            		
//            		assertTrue(Files.size(Paths.get(r)) == 0);
            	}
            	
            	MY_SQL_CONTAINER.stop();
            	
            	System.out.println();	
            }
              
        } catch (Exception e) {
        	System.err.println(e.getMessage());
        	throw new RuntimeException(e);
        }
	}
}
