package burp.ls;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import burp.iteration.CSVIteration;
import burp.iteration.Iteration;
import burp.iteration.JSONIteration;
import burp.iteration.RDBIteration;
import burp.iteration.XMLIteration;
import burp.util.Util;
import burp.vocabularies.RML;
import net.minidev.json.JSONObject;

public abstract class LogicalSource {

	public Set<Object> nulls = new HashSet<Object>();

	public abstract Iterator<Iteration> iterator();

}

abstract class FileBasedLogicalSource extends LogicalSource {

	protected List<Iteration> iterations = null;
	public String file;
	public String iterator;
	public Charset encoding = StandardCharsets.UTF_8;
	public Resource compression = RML.none;

	public String getDecompressedFile() {
		return Util.getDecompressedFile(file, compression);
	}

}

class CSVSource extends FileBasedLogicalSource {

	public char delimiter = ',';
	public Boolean firstLineIsHeader = true;

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				FileReader fr = new FileReader(getDecompressedFile(), encoding);
				
				CSVReader reader = new CSVReaderBuilder(fr)
			    .withCSVParser(new CSVParserBuilder()
			        .withSeparator(delimiter)
			        .build()
			    ).build();
				
				
				List<String[]> all = reader.readAll();
				reader.close();

				String[] header = null;
				
				// IF THE FIRST LINE IS THE HEADER, REMOVE THE FIRST FROM CSV
				// OTHERWISE, CREATE A LIST OF NUMBERED COLUMNS STARTING FROM ONE
				if(firstLineIsHeader)
					header = all.remove(0);
				else {
					int n = all.get(0).length;
					header = new String[n];
					for(int i = 0; i < n; i++)
						header[i] = Integer.toString(i + 1);
				}
				
				for (String[] rec : all) {
					iterations.add(new CSVIteration(header, rec, nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class JSONSource extends FileBasedLogicalSource {

	private static Configuration c = Configuration.builder().mappingProvider(new JacksonMappingProvider())
			.jsonProvider(new JacksonJsonProvider()).build().addOptions(Option.ALWAYS_RETURN_LIST);

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();
				String contents = Files.readString(Paths.get(getDecompressedFile()), encoding);

				List<Map<String, Object>> nodes = JsonPath.using(c).parse(contents).read(iterator);
				for (Map<String, Object> n : nodes) {
					iterations.add(new JSONIteration(JSONObject.toJSONString(n), nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class XMLSource extends FileBasedLogicalSource {

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				String contents = Files.readString(Paths.get(getDecompressedFile()), encoding);

				DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = builderFactory.newDocumentBuilder();

				Document xmlDocument = builder.parse(IOUtils.toInputStream(contents, encoding));

				XPath xPath = XPathFactory.newInstance().newXPath();
				NodeList nodes = (NodeList) xPath.compile(iterator).evaluate(xmlDocument, XPathConstants.NODESET);

				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					iterations.add(new XMLIteration(node, nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class RDBSource extends LogicalSource {

	public String jdbcDriver;
	public String jdbcDSN;
	public String password;
	public String username;
	public String query;

	@Override
	public Iterator<Iteration> iterator() {
		try {
			Properties props = new Properties();
			if (username != null && !"".equals(username))
				props.setProperty("user", username);
			if (password != null && !"".equals(password))
				props.setProperty("password", password);

			Class.forName(jdbcDriver);
			Connection connection = DriverManager.getConnection(jdbcDSN, props);
			Statement statement = connection.createStatement();
			final ResultSet resultset = statement.executeQuery(query);

			Map<String, Integer> indexMap = new HashMap<String, Integer>();
			for (int i = 1; i <= resultset.getMetaData().getColumnCount(); i++) {
				indexMap.put(resultset.getMetaData().getColumnLabel(i), i);
			}

			return new Iterator<Iteration>() {

				@Override
				public boolean hasNext() {
					try {
						return resultset.next();
					} catch (SQLException e) {
						throw new RuntimeException("Problem querying database while iterating over rows.");
					}
				}

				@Override
				public Iteration next() {
					return new RDBIteration(resultset, indexMap, nulls);
				}

			};
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class SPARQLCSVSource extends FileBasedLogicalSource {

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