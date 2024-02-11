package burp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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
	
    @Test
    public void testMappingsCSV() {
    	try (Stream<Path> stream = Files.list(Paths.get("./test/rml-core/"))) {
            List<String> files = stream
              .filter(f -> Files.isDirectory(f) && f.getFileName().toString().contains("CSV"))
              .map(Path::getFileName)
              .map(Path::toString)
              .collect(Collectors.toList());
              
            for(String f : files) {
            	System.out.println(String.format("Now processing %s", f));
            	
            	String m = new File("./test/rml-core/" + f, "mapping.ttl").getAbsolutePath().toString();
            	
            	String r = Files.createTempFile(null, ".nq").toString();
            	System.out.println(String.format("Writing output to %s", r));
            	
            	if(new File("./test/rml-core/" + f, "output.nq").exists()) {
                	String o = new File("./test/rml-core/" + f, "output.nq").getAbsolutePath().toString();
                	

            		Main.doMain(new String[] { "-m", m, "-o", r });

            		Model expected = RDFDataMgr.loadModel(o);
            		Model actual = RDFDataMgr.loadModel(r);
            		
            		if(!expected.isIsomorphicWith(actual)) {
            			expected.write(System.out);
            			actual.write(System.out);
            		}
            		
            		assertTrue(expected.isIsomorphicWith(actual));
            		
            	} else {
            		Main.doMain(new String[] { "-m", m, "-o", r });            		
            		assertTrue(Files.size(Paths.get(r)) == 0);
            	}
            	
            	System.out.println();	
            }
              
        } catch (Exception e) {
        	System.err.println(e.getMessage());
        	throw new RuntimeException(e);
        }
    }
	
}