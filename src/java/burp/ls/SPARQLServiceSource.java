package burp.ls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;

import burp.model.Iteration;
import burp.model.LogicalSource;

class SPARQLServiceSource extends LogicalSource {

	private List<Iteration> iterations = null;
	private boolean isTSV;

	public String iterator;
	public String endpoint;

	public SPARQLServiceSource(boolean isTSV) {
		this.isTSV = isTSV;
	}

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				QueryExecution exec = QueryExecutionHTTP.service(endpoint).query(iterator).build();
				ResultSet results = exec.execSelect();

				while (results.hasNext()) {
					QuerySolution sol = results.next();

					if (isTSV)
						iterations.add(new SPARQLTSVIteratation(sol, nulls));
					else
						iterations.add(new SPARQLIteratation(sol, nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}