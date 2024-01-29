package urml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;

public class Parse {

	public static List<TriplesMap> parseMappingFile(String mappingFile) throws Exception {
		Map<Resource, TriplesMap> triplesmaps = new HashMap<Resource, TriplesMap>();
		
		Model mapping = RDFDataMgr.loadModel(mappingFile);
		
		// Replace rml:subject, rml:object, ... with constant expression maps
		normalizeConstants(mapping);

		// Look for the triples maps
		List<Resource> list = mapping.listSubjectsWithProperty(RML.logicalSource).toList();

		// Process each triples map
		for (Resource r : list) {
			TriplesMap tm = new TriplesMap();
			
			Resource ls = r.getPropertyResourceValue(RML.logicalSource);
			tm.logicalSource = prepareLogicalSource(ls);
			
			Resource sm = r.getPropertyResourceValue(RML.subjectMap);
			tm.subjectMap = prepareSubjectMap(sm);
			//tm.predicateObjectMaps.addAll(classesToPredicateObjectMap(sm));

			triplesmaps.put(r, tm);
		}

		return new ArrayList<TriplesMap>(triplesmaps.values());
	}

	private static void normalizeConstants(Model mapping) {
		String CONSTRUCTSMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:subjectMap [ r:constant ?y ]. } WHERE { ?x r:subject ?y. }";
		String CONSTRUCTOMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:objectMap [ r:constant ?y ]. } WHERE { ?x r:object ?y. }";
		String CONSTRUCTPMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:predicateMap [ r:constant ?y ]. } WHERE { ?x r:predicate ?y. }";
		String CONSTRUCTGMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:graphMap [ r:constant ?y ]. } WHERE { ?x r:graph ?y. }";
		String CONSTRUCTLMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:languageMap [ r:constant ?y ]. } WHERE { ?x r:language ?y. }";	
		String CONSTRUCTDMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:datatypeMap [ r:constant ?y ]. } WHERE { ?x r:datatype ?y. }";
		
		mapping.add(QueryExecutionFactory.create(CONSTRUCTSMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTOMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTPMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTGMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTLMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTDMAPS, mapping).execConstruct());
	}

	private static LogicalSource prepareLogicalSource(Resource ls) throws Exception {
		Resource referenceFormulation = ls.getPropertyResourceValue(RML.referenceFormulation);
		
		if (QL.CSV.equals(referenceFormulation)) {
			String file = ls.getProperty(RML.source).getLiteral().getString();
			CSVSource source = new CSVSource();
			source.file = file;
			return source;
		}

		System.err.println(referenceFormulation);
		throw new Exception("Reference formulation not (yet) supported.");
	}

	private static SubjectMap prepareSubjectMap(Resource sm) {
		SubjectMap subjectMap = new SubjectMap();
		subjectMap.expression = prepareExpression(sm);
		
		sm.listProperties(RML.clazz).forEach(s -> {
			subjectMap.classes.add(s.getObject().asResource());
		});
		
		sm.listProperties(RML.graphMap).forEach(s -> {
			GraphMap gm = new GraphMap();
			gm.expression = prepareExpression(s.getObject().asResource());
			subjectMap.graphMaps.add(gm);
		});	
		
		return subjectMap;
	}

	private static Expression prepareExpression(Resource r) {
		if (r.hasProperty(RML.constant)) {
			RDFNode constant = r.getProperty(RML.constant).getObject();
			return new TermConstant(constant); 
		}

		if (r.hasProperty(RML.reference)) {
			String reference = r.getProperty(RML.reference).getObject().asLiteral().getString();
			return new Reference(reference); 
		}

		if (r.hasProperty(RML.template)) {
			String template = r.getProperty(RML.template).getObject().asLiteral().getString();
			return new Template(template); 
		}

		return null;
	}
	


}
