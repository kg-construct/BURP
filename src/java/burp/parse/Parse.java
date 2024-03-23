package burp.parse;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.util.FileUtils;
import org.apache.jena.util.iterator.ExtendedIterator;

import burp.ls.LogicalSourceFactory;
import burp.model.ConcreteExpressionMap;
import burp.model.DatatypeMap;
import burp.model.Expression;
import burp.model.FunctionExecution;
import burp.model.FunctionMap;
import burp.model.GraphMap;
import burp.model.Input;
import burp.model.InputValueMap;
import burp.model.JoinCondition;
import burp.model.LanguageMap;
import burp.model.LogicalSource;
import burp.model.ObjectMap;
import burp.model.ParameterMap;
import burp.model.PredicateMap;
import burp.model.PredicateObjectMap;
import burp.model.RDFNodeConstant;
import burp.model.Reference;
import burp.model.ReferencingObjectMap;
import burp.model.ReturnMap;
import burp.model.SubjectMap;
import burp.model.Template;
import burp.model.TriplesMap;
import burp.model.gathermaputil.GatherMapMixin;
import burp.vocabularies.RML;

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
		core.read(Parse.class.getResourceAsStream("/shapes/cc.ttl"), "urn:dummy", FileUtils.langTurtle);  
		core.read(Parse.class.getResourceAsStream("/shapes/io.ttl"), "urn:dummy", FileUtils.langTurtle);  
		core.read(Parse.class.getResourceAsStream("/shapes/fnml.ttl"), "urn:dummy", FileUtils.langTurtle); 
		
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
		
		mapping.add(QueryExecutionFactory.create(CONSTRUCTSMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTOMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTPMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTGMAPS, mapping).execConstruct());
		
		String CONSTRUCTLMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:languageMap [ r:constant ?y ]. } WHERE { ?x r:language ?y. }";	
		String CONSTRUCTDMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:datatypeMap [ r:constant ?y ]. } WHERE { ?x r:datatype ?y. }";
		
		mapping.add(QueryExecutionFactory.create(CONSTRUCTLMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTDMAPS, mapping).execConstruct());
		
		String CONSTRUCTChMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:childMap [ r:reference ?y ]. } WHERE { ?x r:child ?y. }";	
		String CONSTRUCTPaMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:parentMap [ r:reference ?y ]. } WHERE { ?x r:parent ?y. }";
		
		mapping.add(QueryExecutionFactory.create(CONSTRUCTChMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTPaMAPS, mapping).execConstruct());
		
		String CONSTRUCTRETURNMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:returnMap [ r:constant ?y ]. } WHERE { ?x r:return ?y. }";	
		String CONSTRUCTFUNCTIONMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:functionMap [ r:constant ?y ]. } WHERE { ?x r:function ?y. }";	
		String CONSTRUCTPARAMETERMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:parameterMap [ r:constant ?y ]. } WHERE { ?x r:parameter ?y. }";	
		String INPUTVALUEMAPS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:inputValueMap [ r:constant ?y ]. } WHERE { ?x r:inputValue ?y. }";	
		
		mapping.add(QueryExecutionFactory.create(CONSTRUCTRETURNMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTFUNCTIONMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(CONSTRUCTPARAMETERMAPS, mapping).execConstruct());
		mapping.add(QueryExecutionFactory.create(INPUTVALUEMAPS, mapping).execConstruct());
		
		String TERMTYPESTOCONSTANTS = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:constant ?y ; r:termType ?z . } WHERE { ?x r:constant ?y. BIND(IF(ISLITERAL(?y), r:Literal, IF(ISIRI(?y), r:IRI, r:BlankNode)) AS ?z)}";
		mapping.add(QueryExecutionFactory.create(TERMTYPESTOCONSTANTS, mapping).execConstruct());
		
		// Graph maps, subject maps, and object maps can have no reference
		// They will generate blank nodes, thus add term type BN
		String IMPLICITTERMTYPE = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:termType r:BlankNode } WHERE { [] r:subjectMap ?x . OPTIONAL { ?x r:template ?a } OPTIONAL { ?x r:reference ?b }  OPTIONAL { ?x r:constant ?c }  OPTIONAL { ?x r:functionExecution ?d } FILTER(!BOUND(?a) && !BOUND(?b) && !BOUND(?c) && !BOUND(?d)) }";
		mapping.add(QueryExecutionFactory.create(IMPLICITTERMTYPE, mapping).execConstruct());
		IMPLICITTERMTYPE = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:termType r:BlankNode } WHERE { [] r:graphMap ?x . OPTIONAL { ?x r:template ?a } OPTIONAL { ?x r:reference ?b }  OPTIONAL { ?x r:constant ?c }  OPTIONAL { ?x r:functionExecution ?d } FILTER(!BOUND(?a) && !BOUND(?b) && !BOUND(?c) && !BOUND(?d)) }";
		mapping.add(QueryExecutionFactory.create(IMPLICITTERMTYPE, mapping).execConstruct());
		IMPLICITTERMTYPE = "PREFIX r: <http://w3id.org/rml/> CONSTRUCT { ?x r:termType r:BlankNode } WHERE { [] r:objectMap ?x . OPTIONAL { ?x r:template ?a } OPTIONAL { ?x r:reference ?b }  OPTIONAL { ?x r:constant ?c }  OPTIONAL { ?x r:functionExecution ?d } FILTER(!BOUND(?a) && !BOUND(?b) && !BOUND(?c) && !BOUND(?d)) }";
		mapping.add(QueryExecutionFactory.create(IMPLICITTERMTYPE, mapping).execConstruct());
	}

	private static LogicalSource prepareLogicalSource(Resource ls, String mpath) throws Exception {
		Resource referenceFormulation = ls.getPropertyResourceValue(RML.referenceFormulation);

		if (RML.CSV.equals(referenceFormulation))
			return LogicalSourceFactory.createCSVSource(ls, mpath);
		
		if (RML.JSONPath.equals(referenceFormulation))
			return LogicalSourceFactory.createJSONSource(ls, mpath);
		
		if (RML.XPath.equals(referenceFormulation)) 
			return LogicalSourceFactory.createXMLSource(ls, mpath);
		
		if (RML.SQL2008Table.equals(referenceFormulation)) 
			return LogicalSourceFactory.createXMLSource(ls, mpath);
		
		if (RML.SQL2008Query.equals(referenceFormulation)) 
			return LogicalSourceFactory.createXMLSource(ls, mpath);
		
		if(RML.SPARQL_Results_CSV.equals(referenceFormulation)) 
			return LogicalSourceFactory.createSPARQLSource(ls, mpath, false);
		
		if(RML.SPARQL_Results_TSV.equals(referenceFormulation)) 
			return LogicalSourceFactory.createSPARQLSource(ls, mpath, true);
		
		if(RML.SPARQL_Results_XML.equals(referenceFormulation)) 
			return LogicalSourceFactory.createSPARQLSource(ls, mpath, false);
		
		if(RML.SPARQL_Results_JSON.equals(referenceFormulation)) 
			return LogicalSourceFactory.createSPARQLSource(ls, mpath, false);

		throw new Exception("Reference formulation not (yet) supported.");
	}

	private static SubjectMap prepareSubjectMap(Resource sm) {
		SubjectMap subjectMap = new SubjectMap();
		subjectMap.expression = prepareExpression(sm);

		sm.listProperties(RML.clazz).forEach(s -> {
			subjectMap.classes.add(s.getObject().asResource());
		});

		sm.listProperties(RML.graphMap).forEach(s -> {
			GraphMap gm = prepareGraphMap(s.getObject().asResource());
			subjectMap.graphMaps.add(gm);
		});	

		Resource termType = sm.getPropertyResourceValue(RML.termType);
		if(termType != null)
			// PROVIDE THE TERM TYPE THAT IS GIVEN
			subjectMap.termType = termType;
		else if(hasNoTemplateReferenceConstantOrFunction(sm)) {
			// IF NO REFERENCE, TEMPLATE, CONSTANT, OR FUNCTION
			// THEN WE GENERATE BLANK NODES (BASED ON THE ITERATION)
			subjectMap.termType = RML.BLANKNODE;
		}
		
		Resource gm = sm.getPropertyResourceValue(RML.gather);
		if(gm != null) {
			// This object map has a gather, so we process it as a gather map
			subjectMap.gatherMap = prepareGatherMap(sm);
		}

		return subjectMap;
	}

	private static PredicateObjectMap preparePredicateObjectMap(Resource pom) {
		PredicateObjectMap predicateObjectMap = new PredicateObjectMap();

		pom.listProperties(RML.graphMap).forEach(s -> {
			GraphMap gm = prepareGraphMap(s.getObject().asResource());			
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

	private static GraphMap prepareGraphMap(Resource r) {
		GraphMap gm = new GraphMap();
		
		gm.expression = prepareExpression(r);
		
		Resource termType = r.getPropertyResourceValue(RML.termType);
		if(termType != null)
			// PROVIDE THE TERM TYPE THAT IS GIVEN
			gm.termType = termType;
		else if(hasNoTemplateReferenceConstantOrFunction(r)) {
			// IF NO REFERENCE, TEMPLATE, CONSTANT, OR FUNCTION
			// THEN WE GENERATE BLANK NODES (BASED ON THE ITERATION)
			gm.termType = RML.BLANKNODE;
		}
		
		return gm;
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
			// PROVIDE THE TERM TYPE THAT IS GIVEN
			objectMap.termType = termType;
		else if(hasNoTemplateReferenceConstantOrFunction(om)) {
			// IF NO REFERENCE, TEMPLATE, CONSTANT, 
			// OR FUNCTION THEN WE GENERATE BLANK NODES (BASED ON THE ITERATION)
			objectMap.termType = RML.BLANKNODE;
		}

		Resource lam = om.getPropertyResourceValue(RML.languageMap);
		if(lam != null)
			objectMap.languageMap = prepareLanguageMap(lam);

		Resource dtm = om.getPropertyResourceValue(RML.datatypeMap);
		if(dtm != null)
			objectMap.datatypeMap = prepareDatatypeMap(dtm);

		if(termType == null && (lam != null || dtm != null || objectMap.expression instanceof Reference || objectMap.expression instanceof FunctionExecution))
			objectMap.termType = RML.LITERAL;
		
		Resource gm = om.getPropertyResourceValue(RML.gather);
		if(gm != null) {
			// This object map has a gather, so we process it as a gather map
			objectMap.gatherMap = prepareGatherMap(om);
		}

		return objectMap;
	}

	private static GatherMapMixin prepareGatherMap(Resource gm) {
		GatherMapMixin gatherMap = new GatherMapMixin();
		
		if(gm.hasProperty(RML.allowEmptyListAndContainer)) {
			boolean empty = gm.getProperty(RML.allowEmptyListAndContainer).getObject().asLiteral().getBoolean();
			gatherMap.allowEmptyListAndContainer = empty;
		}
		
		if(gm.hasProperty(RML.gatherAs)) {
			Resource r = gm.getPropertyResourceValue(RML.gatherAs);
			gatherMap.gatherAs = r;
		}
		
		if(gm.hasProperty(RML.strategy)) {
			Resource r = gm.getPropertyResourceValue(RML.strategy);
			gatherMap.strategy = r;
		}
		
		RDFList list = gm.getPropertyResourceValue(RML.gather).as(RDFList.class);
		ExtendedIterator<RDFNode> iter = list.iterator();
		while(iter.hasNext()) {
			Resource r = iter.next().asResource();
			
			if(r.hasProperty(RML.parentTriplesMap)) {
				ReferencingObjectMap rom = prepareReferencingObjectMap(r);
				gatherMap.gatherMaps.add(rom);
			} else {
				ObjectMap om = prepareObjectMap(r);
				gatherMap.gatherMaps.add(om);
			}
		}

		return gatherMap;
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
		
		if (r.hasProperty(RML.functionExecution)) {
			Resource fer = r.getPropertyResourceValue(RML.functionExecution);
			
			FunctionExecution fe = new FunctionExecution();
			fe.functionMap = prepareFunctionMap(fer.getPropertyResourceValue(RML.functionMap));
			
			if(fer.hasProperty(RML.returnMap))
				fe.returnMap = prepareReturnMap(fer.getPropertyResourceValue(RML.returnMap));
			
			StmtIterator iter = fer.listProperties(RML.input);
			while(iter.hasNext()) {
				Statement x = iter.next();
				fe.inputs.add(prepareInput(x.getObject().asResource()));
			}
			
			return fe;
		}

		return null;
	}
	
	private static Input prepareInput(Resource r) {
		Input input = new Input();
		
		ParameterMap pm = new ParameterMap();
		pm.expression = prepareExpression(r.getPropertyResourceValue(RML.parameterMap));
		input.parameterMap = pm;
		
		
		input.inputValueMap = prepareInputValueMap(r.getPropertyResourceValue(RML.inputValueMap));
		
		return input;
	}

	private static FunctionMap prepareFunctionMap(Resource r) {
		FunctionMap fm = new FunctionMap();
		fm.expression = prepareExpression(r);
		return fm;
	}
	
	private static ReturnMap prepareReturnMap(Resource r) {
		ReturnMap rm = new ReturnMap();
		rm.expression = prepareExpression(r);
		return rm;
	}
	
	private static InputValueMap prepareInputValueMap(Resource om) {
		InputValueMap im = new InputValueMap();
		im.expression = prepareExpression(om);

		Resource termType = om.getPropertyResourceValue(RML.termType);
		if(termType != null)
			im.termType = termType;
		
		Resource lam = om.getPropertyResourceValue(RML.languageMap);
		if(lam != null)
			im.languageMap = prepareLanguageMap(lam);

		Resource dtm = om.getPropertyResourceValue(RML.datatypeMap);
		if(dtm != null)
			im.datatypeMap = prepareDatatypeMap(dtm);

		if(termType == null && (lam != null || dtm != null || im.expression instanceof Reference || im.expression instanceof FunctionExecution))
			im.termType = RML.LITERAL;
		
		return im;
	}

	private static boolean hasNoTemplateReferenceConstantOrFunction(Resource r) {
		if (r.hasProperty(RML.constant)) return false;
		if (r.hasProperty(RML.reference)) return false;
		if (r.hasProperty(RML.template)) return false;
		if (r.hasProperty(RML.functionExecution)) return false;
		return true;
	}

}
