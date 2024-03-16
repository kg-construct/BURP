package burp.model;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class LogicalSource {

	public Set<Object> nulls = new HashSet<Object>();

	public abstract Iterator<Iteration> iterator();

}

//class SPARQLTSVSource extends FileBasedLogicalSource {
//
//	@Override
//	protected Iterator<Iteration> iterator() {
//		try {
//			if (iterations == null) {
//				iterations = new ArrayList<Iteration>();
//
//				Dataset ds = RDFDataMgr.loadDataset(file);
//
//				try (QueryExecution exec = QueryExecution.dataset(ds).query(iterator).build()) {
//					org.apache.jena.query.ResultSet results = exec.execSelect();
//					ByteArrayOutputStream bout = new ByteArrayOutputStream();
//					ResultSetFormatter.outputAsTSV(bout, results);
//
//					TsvParserSettings settings = new TsvParserSettings();
//					TsvParser parser = new TsvParser(settings);
//					List<String[]> all = parser.parseAll(new StringReader(bout.toString()));
//					
//					System.err.println(bout.toString());
//					
//					String[] header = all.remove(0);
//					for (String[] rec : all) {
//						iterations.add(new SPARQLTSVIteration(header, rec));
//					}
//				}
//			}
//			return iterations.iterator();
//		} catch (Throwable e) {
//			throw new RuntimeException(e);
//		}
//	}
//
//}

//class SPARQLJSONSource extends JSONSource {
//
//	private static Configuration c = Configuration.builder().mappingProvider(new JacksonMappingProvider())
//			.jsonProvider(new JacksonJsonProvider()).build().addOptions(Option.ALWAYS_RETURN_LIST);
//
//	@Override
//	protected Iterator<Iteration> iterator() {
//		try {
//			if (iterations == null) {
//				iterations = new ArrayList<Iteration>();
//
//				Dataset ds = RDFDataMgr.loadDataset(file);
//
//				try (QueryExecution exec = QueryExecution.dataset(ds).query(iterator).build()) {
//					org.apache.jena.query.ResultSet results = exec.execSelect();
//					ByteArrayOutputStream bout = new ByteArrayOutputStream();
//					ResultSetFormatter.outputAsJSON(bout, results);
//					
//					List<Map<String, Object>> nodes = JsonPath.using(c).parse(bout.toString()).read(iterator);
//					for (Map<String, Object> n : nodes) {
//						iterations.add(new JSONIteration(JSONObject.toJSONString(n)));
//					}
//				}
//
//			}
//			return iterations.iterator();
//		} catch (Throwable e) {
//			throw new RuntimeException(e);
//		}
//	}
//
//}