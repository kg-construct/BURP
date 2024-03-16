package burp.ls;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.riot.RDFDataMgr;

import com.opencsv.CSVReader;

import burp.model.Iteration;

class SPARQLCSVSource extends FileBasedLogicalSource {

	// TODO: WE SHOULD HAVE A SEPERATE ITERATION FOR SPARQL
	
	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				Dataset ds = RDFDataMgr.loadDataset(file);

				try (QueryExecution exec = QueryExecution.dataset(ds).query(iterator).build()) {
					org.apache.jena.query.ResultSet results = exec.execSelect();
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					ResultSetFormatter.outputAsCSV(bout, results);

					CSVReader reader = new CSVReader(new StringReader(bout.toString()));
					List<String[]> all = reader.readAll();
					reader.close();

					String[] header = all.remove(0);
					for (String[] rec : all) {
						iterations.add(new CSVIteration(header, rec, nulls));
					}
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}