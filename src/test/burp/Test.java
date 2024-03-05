package burp;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.util.FileUtils;
import org.apache.jena.vocabulary.RDF;

public class Test {

	public static void main(String[] args) {
		Model core = ModelFactory.createDefaultModel();
		core.read(Parse.class.getResourceAsStream("/shapes/core.ttl"), "urn:dummy", FileUtils.langTurtle); 
		core.read(Parse.class.getResourceAsStream("/shapes/cc.ttl"), "urn:dummy", FileUtils.langTurtle); 
		
		Model mapping = RDFDataMgr.loadModel("./src/test/burp/rml-core/RMLTC0012a-MySQL/mapping.ttl");
		
		ValidationReport report = ShaclValidator.get().validate(core.getGraph(), mapping.getGraph());
	    ShLib.printReport(report);

	}

}
