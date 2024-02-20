package burp;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.util.FileUtils;

public class Parse {

	static Map<Resource, TriplesMap> triplesmaps = null;

	public static List<TriplesMap> parseMappingFile(String mappingFile) throws Exception {
		String mpath = Paths.get(mappingFile).toAbsolutePath().getParent().toString();

		triplesmaps = new HashMap<Resource, TriplesMap>();

		Model mapping = RDFDataMgr.loadModel(mappingFile);
		
		if(!isValid(mapping))
			throw new RuntimeException("Mapping did not satisfy shapes.");

		// Replace rml:subject, rml:object, ... with constant expression maps
		normalizeConstants(mapping);

		// Look for the triples maps
		List<Resource> list = mapping.listSubjectsWithProperty(RML.logicalSource).toList();

		// Process each triples map
		for (Resource r : list) {
			TriplesMap tm = triplesmaps.computeIfAbsent(r, (x) -> new TriplesMap());

			Resource ls = r.getPropertyResourceValue(RML.logicalSource);
			tm.logicalSource = prepareLogicalSource(ls, mpath);

			Resource sm = r.getPropertyResourceValue(RML.subjectMap);
			tm.subjectMap = prepareSubjectMap(sm);

			r.listProperties(RML.predicateObjectMap).forEach((s) -> {
				PredicateObjectMap pom = preparePredicateObjectMap(s.getObject().asResource());
				tm.predicateObjectMaps.add(pom);
			});
		}

		return new ArrayList<TriplesMap>(triplesmaps.values());
	}

	private static boolean isValid(Model mapping) {		
		Model core = ModelFactory.createDefaultModel();
		core.read(Parse.class.getResourceAsStream("/shapes/core.ttl"), "urn:dummy", FileUtils.langTurtle); 
		
		ValidationReport report = ShaclValidator.get().validate(core.getGraph(), mapping.getGraph());
	    if(!report.conforms()) {
	    	ShLib.printReport(report);
	    	return false;
	    }
	 
		return true;
	}

	private static void normalizeConstants(Model mapping) {
		String CONSTRUCTSMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:subjectMap [ r:constant ?y ]. } WHERE { ?x r:subject ?y. }";
		String CONSTRUCTOMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:objectMap [ r:constant ?y ]. } WHERE { ?x r:object ?y. }";
		String CONSTRUCTPMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:predicateMap [ r:constant ?y ]. } WHERE { ?x r:predicate ?y. }";
		String CONSTRUCTGMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:graphMap [ r:constant ?y ]. } WHERE { ?x r:graph ?y. }";
		String CONSTRUCTLMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:languageMap [ r:constant ?y ]. } WHERE { ?x r:language ?y. }";	
		String CONSTRUCTDMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:datatypeMap [ r:constant ?y ]. } WHERE { ?x r:datatype ?y. }";
		String CONSTRUCTChMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:childMap [ r:reference ?y ]. } WHERE { ?x r:child ?y. }";	
		String CONSTRUCTPaMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:parentMap [ r:reference ?y ]. } WHERE { ?x r:parent ?y. }";

		mapping.add(QueryExecutionFactory.create(CONSTRUCTSMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTOMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTPMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTGMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTLMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTDMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTChMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTPaMAPS, mapping).execConstruct());
	}

	private static LogicalSource prepareLogicalSource(Resource ls, String mpath) throws Exception {
		Resource referenceFormulation = ls.getPropertyResourceValue(RML.referenceFormulation);

		if (QL.CSV.equals(referenceFormulation)) {
			String file = ls.getProperty(RML.source).getLiteral().getString();
			CSVSource source = new CSVSource();
			source.file = getAbsoluteOrRelative(file, mpath);
			return source;
		}
		
		if (QL.JSONPath.equals(referenceFormulation)) {
			String file = ls.getProperty(RML.source).getLiteral().getString();
			String iterator = ls.getProperty(RML.iterator).getLiteral().getString();
			JSONSource source = new JSONSource();
			source.file = getAbsoluteOrRelative(file, mpath);
			source.iterator = iterator;
			return source;
		}
		
		if(isRelationalDatabase(ls)) {
			Resource s = ls.getPropertyResourceValue(RML.source);
			String jdbcDSN = s.getProperty(D2RQ.jdbcDSN).getLiteral().getString();
			String jdbcDriver = s.getProperty(D2RQ.jdbcDriver).getLiteral().getString();
			String username = s.getProperty(D2RQ.username).getLiteral().getString();
			String password = s.getProperty(D2RQ.password).getLiteral().getString();
			
			String query = null;
			Statement t = ls.getProperty(RML.tableName);
			Statement q = ls.getProperty(RML.sqlQuery);
			if(t != null) {
				query = "(SELECT * FROM `" + t.getLiteral() + "`)";
			} else {
				query = q.getLiteral().toString();
			}
			
			RDBSource source = new RDBSource();
			source.jdbcDSN = jdbcDSN;
			source.jdbcDriver = jdbcDriver;
			source.username = username;
			source.password = password;
			source.query = query;
			return source;
		}

		throw new Exception("Reference formulation not (yet) supported.");
	}

	private static boolean isRelationalDatabase(Resource ls) {
		if(ls.getPropertyResourceValue(RML.sqlVersion) != null)
			return true;
		if(ls.getPropertyResourceValue(RML.tableName) != null)
			return true;
		if(ls.getPropertyResourceValue(RML.sqlQuery) != null)
			return true;
		return false;
	}

	private static String getAbsoluteOrRelative(String file, String mpath) {
		if(new File(file).isAbsolute())
			return file;
		return new File(mpath, file).getAbsolutePath();
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

		Resource termType = sm.getPropertyResourceValue(RML.termType);
		if(termType != null)
			subjectMap.termType = termType;

		return subjectMap;
	}

	private static PredicateObjectMap preparePredicateObjectMap(Resource pom) {
		PredicateObjectMap predicateObjectMap = new PredicateObjectMap();

		pom.listProperties(RML.graphMap).forEach(s -> {
			GraphMap gm = new GraphMap();
			gm.expression = prepareExpression(s.getObject().asResource());
			predicateObjectMap.graphMaps.add(gm);
		});	

		pom.listProperties(RML.predicateMap).forEach((s) -> {
			PredicateMap pm = preparePredicateMap(s.getObject().asResource());
			predicateObjectMap.predicateMaps.add(pm);
		});

		pom.listProperties(RML.objectMap).forEach((s) -> {
			if(s.getObject().asResource().getProperty(RML.parentTriplesMap) == null) {
				ObjectMap om = prepareObjectMap(s.getObject().asResource());
				predicateObjectMap.objectMaps.add(om);				
			} else {
				ReferencingObjectMap rom = prepareReferencingObjectMap(s.getObject().asResource());
				predicateObjectMap.refObjectMaps.add(rom);
			}

		});

		return predicateObjectMap;
	}

	private static PredicateMap preparePredicateMap(Resource pm) {
		PredicateMap predicateMap = new PredicateMap();
		predicateMap.expression = prepareExpression(pm);
		return predicateMap;
	}

	private static ObjectMap prepareObjectMap(Resource om) {
		ObjectMap objectMap = new ObjectMap();
		objectMap.expression = prepareExpression(om);

		Resource termType = om.getPropertyResourceValue(RML.termType);
		if(termType != null)
			objectMap.termType = termType;

		Resource lam = om.getPropertyResourceValue(RML.languageMap);
		if(lam != null)
			objectMap.languageMap = prepareLanguageMap(lam);

		Resource dtm = om.getPropertyResourceValue(RML.datatypeMap);
		if(dtm != null)
			objectMap.datatypeMap = prepareDatatypeMap(dtm);

		if(termType == null && (lam != null || dtm != null || objectMap.expression instanceof Reference))
			objectMap.termType = RML.LITERAL;

		return objectMap;
	}

	private static DatatypeMap prepareDatatypeMap(Resource dtm) {
		DatatypeMap x = new DatatypeMap();
		x.expression = prepareExpression(dtm);
		return x;
	}

	private static LanguageMap prepareLanguageMap(Resource lam) {
		LanguageMap x = new LanguageMap();
		x.expression = prepareExpression(lam);
		return x;
	}

	private static ReferencingObjectMap prepareReferencingObjectMap(Resource rom) {
		ReferencingObjectMap referencingObjectMap = new ReferencingObjectMap();

		Resource p = rom.getPropertyResourceValue(RML.parentTriplesMap);
		referencingObjectMap.parent = triplesmaps.computeIfAbsent(p, (x) -> new TriplesMap());

		rom.listProperties(RML.joinCondition).forEach((s) -> {
			JoinCondition jc = new JoinCondition();
			
			Resource jcr = s.getObject().asResource();
			
			Resource r = jcr.getPropertyResourceValue(RML.parentMap);
			if(r != null)
				jc.parentMap = prepareExpressionMap(r);

			r = jcr.getPropertyResourceValue(RML.childMap);
			if(r != null)
				jc.childMap = prepareExpressionMap(r);
			
			referencingObjectMap.joinConditions.add(jc);

		});

		return referencingObjectMap;
	}

	private static ConcreteExpressionMap prepareExpressionMap(Resource em) {
		ConcreteExpressionMap e = new ConcreteExpressionMap();
		e.expression = prepareExpression(em);
		return e;
	}

	private static Expression prepareExpression(Resource r) {
		if (r.hasProperty(RML.constant)) {
			RDFNode constant = r.getProperty(RML.constant).getObject();
			return new RDFNodeConstant(constant); 
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
