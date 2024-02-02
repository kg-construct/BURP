package brml;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;

public class Main {
	
	private static Set<RDFNode> def = Set.of(RML.defaultGraph);
	
	public static void main(String[] args) {
		try {
			// Process the configuration file
			Configuration conf = new Configuration(args);

			// Parse the mapping file
			List<TriplesMap> triplesmaps = Parse.parseMappingFile(conf.mappingFile);

			Dataset ds = generate(triplesmaps, conf.baseIRI);

		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(1);
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
				Set<RDFNode> subjects = sm.generateTerms(i, baseIRI);

				// Let sgs be the set of the generated RDF terms 
				// that result from applying each term map in sgm to i
				Set<RDFNode> sgs = new HashSet<RDFNode>();
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
			}
			
		}
		
		RDFDataMgr.write(System.out, ds, RDFFormat.NQ);

		return ds;
	}

	private static void storeTriplesOfSubjectMaps(
			Dataset ds,
			Set<Resource> classes, 
			Set<RDFNode> subjects, 
			Set<RDFNode> graphs) {
		
		for(RDFNode s : subjects)
			for(Resource c : classes)
				for(RDFNode g : graphs)
					if(g.equals(RML.defaultGraph))
						ds.getDefaultModel().add(s.asResource(), RDF.type, c);
					else
						ds.getNamedModel(g.asResource()).add(s.asResource(), RDF.type, c);
	}

}


