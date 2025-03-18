package burp;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.RDF;

import burp.model.GraphMap;
import burp.model.Iteration;
import burp.model.ObjectMap;
import burp.model.PredicateMap;
import burp.model.PredicateObjectMap;
import burp.model.ReferencingObjectMap;
import burp.model.SubjectMap;
import burp.model.TriplesMap;
import burp.model.gathermaputil.SubGraph;
import burp.parse.Parse;
import burp.util.BURPConfiguration;
import burp.vocabularies.RML;

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

			if (conf.outputFile != null)
				RDFDataMgr.write(new FileOutputStream(conf.outputFile), ds, Lang.NQ);
			else
				RDFDataMgr.write(System.out, ds, Lang.NQ);

			// It all went well, thus return 0
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
			return 1;
		}
	}

	private static Dataset generate(List<TriplesMap> triplesmaps, String givenBaseIRI) {
		Dataset ds = DatasetFactory.create();

		// Execute the triples maps
		for (TriplesMap tm : triplesmaps) {
			String baseIRI = tm.baseIRI == null ? givenBaseIRI : tm.baseIRI;

			// Let sm be the subject map of the triples map
			SubjectMap sm = tm.subjectMap;

			// Let sgm be the set of graph maps of subject maps
			List<GraphMap> sgm = sm.graphMaps;

			// For each iteration i in iterations, apply the following steps:
			Iterator<Iteration> iter = tm.logicalSource.iterator();
			while (iter.hasNext()) {
				Iteration i = iter.next();

				// Let sgs be the set of the generated RDF terms
				// that result from applying each term map in sgm to i
				List<RDFNode> sgs = sgm.isEmpty() ? def : new ArrayList<RDFNode>();
				for (GraphMap gm : sgm) {
					sgs.addAll(gm.generateTerms(i, baseIRI));
				}

				// Let subjects be the generated RDF terms that result from applying sm to i
				List<RDFNode> subjects = new ArrayList<RDFNode>();

				if (!sm.isGatherMap()) {
					subjects.addAll(sm.generateTerms(i, baseIRI));
				} else {
					for (SubGraph subgraph : sm.generateGatherMapGraphs(i, baseIRI)) {
						subjects.add(subgraph.node);
						addToGraphs(ds, sgs, subgraph);
					}
				}

				// For each subject in subjects and each class in classes,
				// add triples to the output dataset as follows:
				// subject: subject
				// predicate: rdf:type
				// object: class
				// target graphs: If sgm is empty: rr:defaultgraph; otherwise: subject_graphs

				storeTriplesOfSubjectMaps(ds, sm.classes, subjects, sgs);

				// For each predicate-object map of the triples map, apply the following steps:
				// Let predicates be the set of generated RDF terms that result
				// from applying each of the predicate-object map's predicate maps to i
				// Let objects be the set of generated RDF terms that result from applying each
				// of the predicate-object map's object maps (but not referencing object maps)
				// to i
				// Let pogm be the set of graph maps of the predicate-object map
				// Let pogs be the set of generated RDF terms that result from applying each
				// graph map in pogm to i
				// For each possible combination <s, p, o> in subjects X predicates X objects,
				// add triples to the output dataset as follows:
				// s: subject
				// p: predicate
				// o: object
				// Target graphs: If sgm and pogm are empty: rr:defaultGraph; otherwise:
				// union of subject_graphs and predicate-object_graphs

				for (PredicateObjectMap pom : tm.predicateObjectMaps) {
					List<RDFNode> pogs = new ArrayList<RDFNode>();
					for (GraphMap gm : pom.graphMaps) {
						pogs.addAll(gm.generateTerms(i, baseIRI));
					}

					List<RDFNode> graphs = def;
					// If sgm and pogm are empty: rr:defaultGraph (see line above)
					if (!sgm.isEmpty() || !pogs.isEmpty()) {
						// otherwise: union of subject_graphs and predicate-object_graphs
						// we do an additional test as sgs contains rml:defaultGraph if sgm is empty
						// we do not want to include that
						pogs.addAll(!sgm.isEmpty() ? sgs : new ArrayList<>());
						graphs = pogs;
					}

					List<RDFNode> predicates = new ArrayList<RDFNode>();
					for (PredicateMap pm : pom.predicateMaps) {
						predicates.addAll(pm.generateTerms(i, baseIRI));
					}

					List<RDFNode> objects = new ArrayList<RDFNode>();

					for (ObjectMap om : pom.objectMaps) {
						if (!om.isGatherMap()) {
							objects.addAll(om.generateTerms(i, baseIRI));
						} else {
							for (SubGraph subgraph : om.generateGatherMapGraphs(i, baseIRI)) {
								objects.add(subgraph.node);
								addToGraphs(ds, graphs, subgraph);
							}
						}
					}

					for (ReferencingObjectMap rom : pom.refObjectMaps) {
						objects.addAll(rom.generateTerms(i, baseIRI));
					}

					storetriples(ds, subjects, predicates, objects, graphs);
				}

			}

		}

		removeJunk(ds);

		return ds;
	}

	private static void removeJunk(Dataset ds) {
		removeJunk(ds.getDefaultModel());
		Iterator<Resource> iter = ds.listModelNames();
		while (iter.hasNext())
			removeJunk(ds.getNamedModel(iter.next()));
	}

	private static void removeJunk(Model model) {
		StmtIterator s = model.listStatements(null, RDF.type, RML.list);
		while (s.hasNext()) {
			Statement statement = s.next();
			s.remove();

			Resource l = statement.getSubject();
			if (!l.hasProperty(RDF.first)) {
				ResourceUtils.renameResource(l, RDF.nil.toString());
			}
		}
	}

	private static void storetriples(Dataset ds, List<RDFNode> subjects, List<RDFNode> predicates,
			List<RDFNode> objects, List<RDFNode> graphs) {

		for (RDFNode s : subjects) {
			Resource sr = s.asResource();
			for (RDFNode p : predicates) {
				Property pp = ResourceFactory.createProperty(p.asResource().getURI());
				for (RDFNode o : objects)
					for (RDFNode g : graphs)
						getModel(ds, g).add(sr, pp, o);
			}
		}
	}

	private static void storeTriplesOfSubjectMaps(Dataset ds, List<Resource> classes, List<RDFNode> subjects,
			List<RDFNode> graphs) {

		for (RDFNode s : subjects) {
			Resource sr = s.asResource();

			for (RDFNode c : classes)
				for (RDFNode g : graphs)
					getModel(ds, g).add(sr, RDF.type, c);
		}
	}

	private static void addToGraphs(Dataset ds, List<RDFNode> graphs, SubGraph subgraph) {
		for (RDFNode graph : graphs) {
			Model g = getModel(ds, graph);
			Resource r = subgraph.node.asResource();

			if(subgraph.isList()) {
				g.add(r, RDF.type, RML.list);

				try {
					RDFList l = g.getList(r);
					RDFList sub = subgraph.model.getList(r);

					List<RDFNode> elements = sub.iterator().toList();
					for(RDFNode e : elements) {
						l.add(e);
					}

					while(true) {
						if(sub.isEmpty())
							break;
						sub = sub.removeHead();
					}

					g.add(subgraph.model);
				} catch (Exception e) {
					// List did not exist, so we can just add it
					g.add(subgraph.model);
				}

			} else {
				Container c = null;
				Container sub = null;
				if(subgraph.isAlt()) {
					g.add(r, RDF.type, RDF.Alt);
					c = g.getAlt(r);
					sub = subgraph.model.getAlt(r);
				} else if(subgraph.isBag()) {
					g.add(r, RDF.type, RDF.Bag);
					c = g.getAlt(r);
					sub = subgraph.model.getBag	(r);
				} else if(subgraph.isSeq()) {
					g.add(r, RDF.type, RDF.Seq);
					c = g.getAlt(r);
					sub = subgraph.model.getSeq(r);
				}

				// Now amend everything so that
				// we append the containers
				List<RDFNode> elements = sub.iterator().toList();
				for(RDFNode e : elements)
					c.add(e);

				StmtIterator iter = sub.listProperties();
				while(iter.hasNext()) {
					Statement s = iter.next();
					if(s.getSubject().equals(r))
						if(s.getPredicate().getURI().startsWith("http://www.w3.org/1999/02/22-rdf-syntax-ns#_"))
							iter.remove();
				}

				// We all all the remaining triples
				g.add(subgraph.model);

			}
		}
	}

	private static Model getModel(Dataset ds, RDFNode g) {
		if (g.equals(RML.defaultGraph))
			return ds.getDefaultModel();
		return ds.getNamedModel(g.asResource());
	}

}
