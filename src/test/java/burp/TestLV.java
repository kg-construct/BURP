package burp;

import burp.model.TriplesMap;
import burp.parse.Parse;
import burp.util.BURPConfiguration;

import java.io.File;
import java.nio.file.Files;

public class TestLV {

    public static void main(String[] args) throws Exception {
        String m = new File("./src/test/resources/rml-lv/RMLLVTC0002a/", "mapping.ttl").getAbsolutePath();
        String r = Files.createTempFile(null, ".nq").toString();
        args = new String[] { "-m", m, "-o", r, "-b", "http://example.com/base/" };
        BURPConfiguration conf = new BURPConfiguration(args);
        TriplesMap t = Parse.parseMappingFile(conf.mappingFile).get(0);
        t.logicalSource.iterator().forEachRemaining(x -> {
            System.out.println(x);
        });
    }

}
