package burp.ls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;

import burp.model.Iteration;

class SPARQLFileSource extends FileBasedLogicalSource {

	private boolean isTSV;

	public SPARQLFileSource(boolean isTSV) {
		this.isTSV = isTSV;
	}

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				Dataset ds = RDFDataMgr.loadDataset(file);

				try (QueryExecution exec = QueryExecution.dataset(ds).query(iterator).build()) {
					ResultSet results = exec.execSelect();
					
					while(results.hasNext()) {
						QuerySolution sol = results.next();
						
						if(isTSV)
							iterations.add(new SPARQLTSVIteratation(sol, nulls));
						else
							iterations.add(new SPARQLIteratation(sol, nulls));
					}
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class SPARQLIteratation extends Iteration {

	private QuerySolution sol = null;
	
	protected SPARQLIteratation(QuerySolution sol, Set<Object> nulls) {
		super(nulls);

		this.sol = sol;
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		RDFNode n = sol.get(reference);
		if(n != null && !nulls.contains(n))
			l.add(n);
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		RDFNode n = sol.get(reference);
		if(n != null && !nulls.contains(n))
			l.add(n.toString());
		return l;
	}
	
}

class SPARQLTSVIteratation extends Iteration {

	private QuerySolution sol = null;
	
	protected SPARQLTSVIteratation(QuerySolution sol, Set<Object> nulls) {
		super(nulls);

		this.sol = sol;
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		// REMOVE THE ? FROM THE REFERENCE
		RDFNode n = sol.get(reference.substring(1));
		if(n != null && !nulls.contains(n))
			l.add(n);
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		// REMOVE THE ? FROM THE REFERENCE
		RDFNode n = sol.get(reference.substring(1));
		if(n != null && !nulls.contains(n))
			l.add(n.toString());
		return l;
	}
	
}