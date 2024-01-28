package urml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

public class Main {

	private static Dataset ds = DatasetFactory.create();
	
	public static void main(String[] args) {
		try {
			// Process the configuration file
			Configuration conf = new Configuration(args);
			
			// Parse the mapping file
			List<TriplesMap> triplesmaps = Parse.parseMappingFile(conf.mappingFile);
			
			// Load the data and create iterators
			for(TriplesMap tm : triplesmaps)
				tm.logicalSource.load();
			
			// Execute the triples maps
			for(TriplesMap tm : triplesmaps) {
				// Let sm be the subject map of the triples map
				SubjectMap sm = tm.subjectMap;
				
				// Let sgm be the set of graph maps of subject maps
				Set<GraphMap> sgm = sm.graphMaps;
				
				// For each iteration i in iterations, apply the following steps:
				for(Iteration i: tm.logicalSource.iterations) {

					// Let subjects be the generated RDF terms that result from applying sm to i
					Set<RDFNode> subjects = generateTerms(i, sm, conf.baseIRI);
					
					System.out.println(subjects);
					
					// Let subject_graphs be the set of the generated RDF terms that result from applying each term map in sgm to i
					Set<RDFNode> subject_graphs = new HashSet<RDFNode>();
					for(GraphMap gm : sgm) {
						subject_graphs.addAll(generateTerms(i, gm, conf.baseIRI));
					}
					
					// For each subject in subjects and each class in classes, add triples to the output dataset as follows:
					// subject: 		subject
					// predicate: 		rdf:type
					// object:			class
					// target graphs: 	If sgm is empty: rr:defaultgraph; otherwise: subject_graphs
					
					for(RDFNode s : subjects)
						for(Resource clazz : sm.classes)
							if(sgm.isEmpty())
								ds.getDefaultModel().add(s.asResource(), RDF.type, clazz);
							else
								for(RDFNode g : subject_graphs)
									if(g == RML.defaultGraph)
										ds.getDefaultModel().add(s.asResource(), RDF.type, clazz);
									else
										ds.getNamedModel(g.asResource()).add(s.asResource(), RDF.type, clazz);
				}
			}
			
			
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	private static Set<RDFNode> generateTerms(Iteration i, TermMap tm, String baseIRI) {
		// If the term map is a constant-valued term map, 
		// then the generated RDF term is the term map's 
		// constant value.
		if(tm.expression instanceof Constant) {
			return tm.expression.values(i);
		}
		
		Set<RDFNode> terms = new HashSet<RDFNode>();
		
		// If the expression map is not defined, then we
		// generate a blank node
		if(tm.expression == null) {
			terms.add(ds.getDefaultModel().createResource());
		}
		
		Set<String> values = tm.expression.values(i);
		
		System.out.println(values);
		
		return terms;
	}

}


