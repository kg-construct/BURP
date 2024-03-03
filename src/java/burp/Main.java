package burp;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;

public class Main {

	private static List<RDFNode> def = List.of(RML.defaultGraph);

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
			e.printStackTrace();
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
			List<GraphMap> sgm = sm.graphMaps;

			// For each iteration i in iterations, apply the following steps:
			Iterator<Iteration> iter = tm.logicalSource.iterator();
			while(iter.hasNext()) {
				Iteration i = iter.next();

				// Let subjects be the generated RDF terms that result from applying sm to i
				List<RDFNode> subjects = sm.generateTerms(i, baseIRI);

				// Let sgs be the set of the generated RDF terms 
				// that result from applying each term map in sgm to i
				List<RDFNode> sgs = new ArrayList<RDFNode>();
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
					List<RDFNode> pogs = new ArrayList<RDFNode>();
					for(GraphMap gm : pom.graphMaps) {
						pogs.addAll(gm.generateTerms(i, baseIRI));
					}

					List<RDFNode> graphs = def;
					if(!sgm.isEmpty() || !pogs.isEmpty()) {
						pogs.addAll(sgs);
						graphs = pogs;
					}
					
					List<RDFNode> predicates = new ArrayList<RDFNode>();
					for(PredicateMap pm : pom.predicateMaps) {
						predicates.addAll(pm.generateTerms(i, baseIRI));
					}

					List<RDFNode> objects = new ArrayList<RDFNode>();
					
					for(ObjectMap om : pom.objectMaps) {
						List<CC> CCs = new ArrayList<>();
						if(!om.isGatherMap()) {
							objects.addAll(om.generateTerms(i, baseIRI));
						} else {
							CCs = om.gatherMap.generateCC(i, baseIRI);
							
							if(om.expression == null) {
								for(CC cc: CCs) {
									RDFNode n = ResourceFactory.createResource();
									objects.add(n);
									addCCFor(ds, graphs, n, cc);
								}
								
							} else {
								for(RDFNode n : om.generateTerms(i, baseIRI)) {
									objects.add(n);
									for(CC cc: CCs) {
										objects.add(n);
										addCCFor(ds, graphs, n, cc);
									}
								}
							}							
						}

					}
					
					for(ReferencingObjectMap rom : pom.refObjectMaps) {
						TriplesMap parent = rom.parent;

						// If there are no join conditions, then we generate resources
						// from the child iteration. This is only guaranteed to work
						// for logical sources of the same type or if the parent triple
						// map' subject map only uses simple references.
						if(rom.joinConditions.isEmpty()) {
							objects.addAll(parent.subjectMap.generateTerms(i, baseIRI));
						} else {
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


					
					storetriples(ds, subjects, predicates, objects, graphs);
				}

			}

		}

		//RDFDataMgr.write(System.out, ds, RDFFormat.NQ);

		return ds;
	}

	private static void storetriples(
			Dataset ds, 
			List<RDFNode> subjects, 
			List<RDFNode> predicates, 
			List<RDFNode> objects,
			List<RDFNode> graphs) {

		for(RDFNode s : subjects) {
			Resource sr = s.asResource();
			for(RDFNode p : predicates) {
				Property pp = ResourceFactory.createProperty(p.asResource().getURI());
				for(RDFNode o : objects)
					for(RDFNode g : graphs)
						getModel(ds, g).add(sr, pp, o);
			}
		}
	}

	private static void storeTriplesOfSubjectMaps(
			Dataset ds,
			List<Resource> classes, 
			List<RDFNode> subjects, 
			List<RDFNode> graphs) {

		for(RDFNode s : subjects) {
			Resource sr = s.asResource();
			
			for(RDFNode c : classes)
				for(RDFNode g : graphs)
					getModel(ds, g).add(sr, RDF.type, c);
		}	
	}
	
	private static void addCCFor(Dataset ds, List<RDFNode> graphs, RDFNode n, CC cc) {
		for(RDFNode graph : graphs) {
			Model g = getModel(ds, graph);
			
			if(cc instanceof BurpCollection) {
				//g.getli
			} else if (cc instanceof BurpBag) {
				g.add(n.asResource(), RDF.type, RDF.Bag);
				Bag c = g.getBag(n.asResource());
				
				for(Node item : cc.nodes) {
					c.add(item.node);
				}
			} else if (cc instanceof BurpSeq) {
				g.add(n.asResource(), RDF.type, RDF.Seq);
				Seq c = g.getSeq(n.asResource());
				
				for(Node item : cc.nodes) {
					c.add(item.node);
				}
			} else if (cc instanceof BurpAlt) {
				g.add(n.asResource(), RDF.type, RDF.Alt);
				Alt c = g.getAlt(n.asResource());
				
				for(Node item : cc.nodes) {
					c.add(item.node);
				}
			}
		}
	}

	private static Model getModel(Dataset ds, RDFNode g) {
		if(g.equals(RML.defaultGraph))
			return ds.getDefaultModel();
		return ds.getNamedModel(g.asResource());
	}

}


