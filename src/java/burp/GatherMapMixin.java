package burp;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import com.github.jsonldjava.shaded.com.google.common.collect.Lists;

import burp.vocabularies.RML;

public class GatherMapMixin {

	public boolean allowEmptyListAndContainer = false;
	public Resource gatherAs = null;
	public Resource strategy = RML.append;
	public List<GatherMap> gatherMaps = new ArrayList<GatherMap>();

	public List<SubGraph> generateGraphs(Iteration i, String baseIRI) {
		if (RML.append.equals(strategy)) {
			return append(i, baseIRI);
		} else if (RML.cartesianProduct.equals(strategy)) {
			return cartesianProduct(i, baseIRI);
		}
		throw new RuntimeException("Unknown strategy.");
	}

	private List<SubGraph> cartesianProduct(Iteration i, String baseIRI) {
		List<SubGraph> graphs = new ArrayList<SubGraph>();

		List<List<SubGraph>> superlist = new ArrayList<List<SubGraph>>();
		for (GatherMap tm : gatherMaps) {
			List<SubGraph> list = new ArrayList<SubGraph>();

			if (tm.isGatherMap()) {
				for (SubGraph g : tm.generateGatherMapGraphs(i, baseIRI)) {
					list.add(g);
				}
			} else {
				for (RDFNode generated : tm.generateTerms(i, baseIRI)) {
					SubGraph sg = new SubGraph();
					sg.node = generated;
					list.add(sg);
				}
			}
			superlist.add(list);		
		}

		List<List<SubGraph>> sets = Lists.cartesianProduct(superlist);
		for (List<SubGraph> list : sets) {
			Model m = ModelFactory.createDefaultModel();
			RDFNode n = m.createResource();
			if (gatherAs.equals(RDF.List))
				createList(m, n, list);
			else
				createContainer(m, n, list);

			if (!m.isEmpty()) {
				SubGraph g = new SubGraph(n, m);
				graphs.add(g);
			}
		}
		
		return graphs;
	}

	private List<SubGraph> append(Iteration i, String baseIRI) {
		List<SubGraph> graphs = new ArrayList<SubGraph>();
		Model m = ModelFactory.createDefaultModel();

		// Let's create a node for the list / bag
		// It may be overwritten if specific names / blank nodes
		// have to be provided in the term map
		RDFNode n = m.createResource();

		List<SubGraph> list = new ArrayList<SubGraph>();
		for (GatherMap tm : gatherMaps) {
			if (tm.isGatherMap()) {
				for (SubGraph g : tm.generateGatherMapGraphs(i, baseIRI)) {
					list.add(g);
				}
			} else {
				for (RDFNode generated : tm.generateTerms(i, baseIRI)) {
					SubGraph sg = new SubGraph();
					sg.node = generated;
					list.add(sg);
				}
			}
		}

		if (gatherAs.equals(RDF.List))
			createList(m, n, list);
		else
			createContainer(m, n, list);

		if (!m.isEmpty()) {
			SubGraph g = new SubGraph(n, m);
			graphs.add(g);
		}

		return graphs;
	}

	private void createList(Model m, RDFNode n, List<SubGraph> list) {
		if (list.size() > 0 || allowEmptyListAndContainer) {
			m.add(n.asResource(), RDF.type, RML.list);

			for (SubGraph sg : list) {
				// Adding the element to the container
				try {
					RDFList l = m.getList(n.asResource());
					l.add(sg.node);
				} catch (Exception e) {
					m.add(n.asResource(), RDF.rest, RDF.nil);
					m.add(n.asResource(), RDF.first, sg.node);
				}

				// Adding any triples around it into the model
				if (sg.model != null)
					m.add(sg.model);
			}
		}
	}

	private void createContainer(Model m, RDFNode n, List<SubGraph> list) {
		if (list.size() > 0 || allowEmptyListAndContainer) {
			Container c = null;
			if (gatherAs.equals(RDF.Alt)) {
				m.add(n.asResource(), RDF.type, RDF.Alt);
				c = m.getAlt(n.asResource());
			} else if (gatherAs.equals(RDF.Bag)) {
				m.add(n.asResource(), RDF.type, RDF.Bag);
				c = m.getBag(n.asResource());

			} else if (gatherAs.equals(RDF.Seq)) {
				m.add(n.asResource(), RDF.type, RDF.Seq);
				c = m.getSeq(n.asResource());
			}

			for (SubGraph sg : list) {
				// Adding the element to the container
				c.add(sg.node);

				// Adding any triples around it into the model
				if (sg.model != null)
					m.add(sg.model);
			}
		}
	}

}
