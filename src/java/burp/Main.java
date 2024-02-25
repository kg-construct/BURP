package burp;

import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;

public class Main {

	private static Set<Resource> def = Set.of(RML.defaultGraph);

	public static void main(String[] args) {
		int exit = doMain(args);
		System.out.println("System exiting with code: " + exit);
		System.exit(exit);
	}

	public static int doMain(String[] args) {
		try {
			// Process the configuration file
			BURPConfiguration conf = new BURPConfiguration(args);

			// Parse the mapping file
			List<TriplesMap> triplesmaps = Parse.parseMappingFile(conf.mappingFile);

			Dataset ds = generate(triplesmaps, conf.baseIRI);

			if(conf.outputFile != null)
				RDFDataMgr.write(new FileOutputStream(conf.outputFile), ds, Lang.NQ);

			// It all went well, thus return 0
			return 0;
		} catch (Exception e) {
			System.err.println(e.getMessage());
			return 1;
		}
	}

	private static Dataset generate(List<TriplesMap> triplesmaps, String baseIRI) {
		Dataset ds = DatasetFactory.create();

		// Execute the triples maps
		for(TriplesMap tm : triplesmaps) {
			// Let sm be the subject map of the triples map
			SubjectMap sm = tm.subjectMap;

			// Let sgm be the set of graph maps of subject maps
			Set<GraphMap> sgm = sm.graphMaps;

			// For each iteration i in iterations, apply the following steps:
			Iterator<Iteration> iter = tm.logicalSource.iterator();
			while(iter.hasNext()) {
				Iteration i = iter.next();

				// Let subjects be the generated RDF terms that result from applying sm to i
				Set<Resource> subjects = sm.generateTerms(i, baseIRI);

				// Let sgs be the set of the generated RDF terms 
				// that result from applying each term map in sgm to i
				Set<Resource> sgs = new HashSet<Resource>();
				for(GraphMap gm : sgm) {
					sgs.addAll(gm.generateTerms(i, baseIRI));
				}

				// For each subject in subjects and each class in classes, 
				// add triples to the output dataset as follows:
				// subject: 		subject
				// predicate: 		rdf:type
				// object:			class
				// target graphs: 	If sgm is empty: rr:defaultgraph; otherwise: subject_graphs

				storeTriplesOfSubjectMaps(ds, sm.classes, subjects, sgm.isEmpty() ? def : sgs);

				// For each predicate-object map of the triples map, apply the following steps:
				// Let predicates be the set of generated RDF terms that result 
				//   from applying each of the predicate-object map's predicate maps to i
				// Let objects be the set of generated RDF terms that result from applying each 
				//   of the predicate-object map's object maps (but not referencing object maps) to i
				// Let pogm be the set of graph maps of the predicate-object map
				// Let pogs be the set of generated RDF terms that result from applying each graph map in pogm to i
				// For each possible combination <s, p, o> in subjects X predicates X objects, 
				//   add triples to the output dataset as follows:
				// s: subject
				// p: predicate
				// o: object
				// Target graphs: If sgm and pogm are empty: rr:defaultGraph; otherwise: 
				// union of subject_graphs and predicate-object_graphs

				for(PredicateObjectMap pom : tm.predicateObjectMaps) {
					Set<Property> predicates = new HashSet<Property>();
					for(PredicateMap pm : pom.predicateMaps) {
						predicates.addAll(pm.generateTerms(i, baseIRI));
					}

					Set<RDFNode> objects = new HashSet<RDFNode>();
					for(ObjectMap om : pom.objectMaps) {
						objects.addAll(om.generateTerms(i, baseIRI));
					}
					for(ReferencingObjectMap rom : pom.refObjectMaps) {
						TriplesMap parent = rom.parent;

						// If there are no join conditions, then we generate resources
						// from the child iteration. This is only guaranteed to work
						// for logical sources of the same type or if the parent triple
						// map' subject map only uses simple references.
						if(rom.joinConditions.isEmpty()) {
							objects.addAll(parent.subjectMap.generateTerms(i, baseIRI));
						} 
						else {
							Iterator<Iteration> iter2 = parent.logicalSource.iterator();
							while(iter2.hasNext()) {
								Iteration i2 = iter2.next();
								
								// Expression Maps are multi-valued. We thus need
								// For each join condition at least one match.
								boolean ok = true;
								for(JoinCondition jc : rom.joinConditions) {
									
									List<String> values1 = jc.childMap.generateValues(i);
									List<String> values2 = jc.parentMap.generateValues(i2);

									if(values1.stream().distinct().filter(values2::contains).collect(Collectors.toSet()).isEmpty()) {
										// No match, break.
										ok = false;
										break;
									}
								}
								
								if(ok) {
									objects.addAll(parent.subjectMap.generateTerms(i2, baseIRI));									
								}
							}
						}
					}

					Set<Resource> pogs = new HashSet<Resource>();
					for(GraphMap gm : pom.graphMaps) {
						pogs.addAll(gm.generateTerms(i, baseIRI));
					}

					Set<Resource> graphs = def;
					if(!sgm.isEmpty() || !pogs.isEmpty()) {
						pogs.addAll(sgs);
						graphs = pogs;
					}
					
					storetriples(ds, subjects, predicates, objects, graphs);
				}

			}

		}

		//RDFDataMgr.write(System.out, ds, RDFFormat.NQ);

		return ds;
	}

	private static void storetriples(
			Dataset ds, 
			Set<Resource> subjects, 
			Set<Property> predicates, 
			Set<RDFNode> objects,
			Set<Resource> graphs) {

		for(Resource s : subjects)
			for(Property p : predicates)
				for(RDFNode o : objects)
					for(Resource g : graphs)
						if(g.equals(RML.defaultGraph))
							ds.getDefaultModel().add(s, p, o);
						else
							ds.getNamedModel(g.asResource()).add(s, p, o);
	}

	private static void storeTriplesOfSubjectMaps(
			Dataset ds,
			Set<Resource> classes, 
			Set<Resource> subjects, 
			Set<Resource> graphs) {

		for(Resource s : subjects)
			for(Resource c : classes)
				for(Resource g : graphs)
					if(g.equals(RML.defaultGraph))
						ds.getDefaultModel().add(s, RDF.type, c);
					else
						ds.getNamedModel(g).add(s, RDF.type, c);
	}

}


