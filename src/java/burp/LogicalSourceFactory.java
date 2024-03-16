package burp;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.VOID;

public class LogicalSourceFactory {

	public static LogicalSource createCSVSource(Resource ls, String mpath) {
		CSVSource source = new CSVSource();

		if (ls.getPropertyResourceValue(RML.source).hasProperty(RDF.type, CSVW.Table)) {
			Resource s = ls.getPropertyResourceValue(RML.source);

			String file = s.getProperty(CSVW.url).getLiteral().getString();
			source.file = getAbsoluteOrRelativeFromFileProtocol(file, mpath);

			// IF IT IS A CSVW TABLE, THEN LOOK FOR THE ENCODING IN THE DIALECT
			if (s.hasProperty(CSVW.dialect)) {
				Resource r = s.getPropertyResourceValue(CSVW.dialect);
				if (r.hasProperty(CSVW.encoding)) {
					String e = r.getProperty(CSVW.encoding).getString();
					if ("UTF-8".equals(e))
						source.encoding = StandardCharsets.UTF_8;
					else if ("UTF-16".equals(e))
						source.encoding = StandardCharsets.UTF_16;
					else
						throw new RuntimeException("Provided Character Set " + r + " not supported.");
				}
				
				if (r.hasProperty(CSVW.delimiter)) {
					// TODO: According to CSVW, the delimiter is a string. But all examples are chars.
					char e = r.getProperty(CSVW.delimiter).getChar();
					source.delimiter = e; 
				}
				
				if (r.hasProperty(CSVW.header)) {
					Boolean e = r.getProperty(CSVW.header).getBoolean();
					source.firstLineIsHeader = e; 
				}				
				
			} else {
				source.encoding = StandardCharsets.UTF_8;
			}

			source.compression = getCompression(ls);

		} else {
			// WE HAVE A SIMPLE CSV FILE (CORE)
			String file = getFile(ls);
			source.file = getAbsoluteOrRelative(file, mpath);
			source.encoding = getEncoding(ls);
			source.compression = getCompression(ls);
		}

		source.nulls.addAll(getNullValues(ls));
		
		return source;
	}

	public static LogicalSource createJSONSource(Resource ls, String mpath) {
		String file = getFile(ls);
		String iterator = ls.getProperty(RML.iterator).getLiteral().getString();
		JSONSource source = new JSONSource();
		source.file = getAbsoluteOrRelative(file, mpath);
		source.iterator = iterator;
		source.encoding = getEncoding(ls);
		source.compression = getCompression(ls);
		source.nulls.addAll(getNullValues(ls));
		return source;
	}

	public static LogicalSource createXMLSource(Resource ls, String mpath) {
		String file = getFile(ls);
		String iterator = ls.getProperty(RML.iterator).getLiteral().getString();
		XMLSource source = new XMLSource();
		source.file = getAbsoluteOrRelative(file, mpath);
		source.iterator = iterator;
		source.encoding = getEncoding(ls);
		source.compression = getCompression(ls);
		source.nulls.addAll(getNullValues(ls));
		return source;
	}

	public static LogicalSource createSQL2008TableSource(Resource ls, String mpath) {
		Resource s = ls.getPropertyResourceValue(RML.source);
		String jdbcDSN = s.getProperty(D2RQ.jdbcDSN).getLiteral().getString();
		String jdbcDriver = s.getProperty(D2RQ.jdbcDriver).getLiteral().getString();
		String username = s.getProperty(D2RQ.username).getLiteral().getString();
		String password = s.getProperty(D2RQ.password).getLiteral().getString();

		Statement t = ls.getProperty(RML.iterator);
		String query = "(SELECT * FROM " + t.getLiteral() + ")";

		RDBSource source = new RDBSource();
		source.jdbcDSN = jdbcDSN;
		source.jdbcDriver = jdbcDriver;
		source.username = username;
		source.password = password;

		// Apache jena "escapes" double quotes, so "Name" becomes \"Name\"
		// which is internally stored as \\"Name\\". We thus need to remove
		// occurrences of \\
		source.query = query.replace("\\", "");
		
		source.nulls.addAll(getNullValues(ls));

		return source;
	}

	public static LogicalSource createSQL2008QuerySource(Resource ls, String mpath) {
		Resource s = ls.getPropertyResourceValue(RML.source);
		String jdbcDSN = s.getProperty(D2RQ.jdbcDSN).getLiteral().getString();
		String jdbcDriver = s.getProperty(D2RQ.jdbcDriver).getLiteral().getString();
		String username = s.getProperty(D2RQ.username).getLiteral().getString();
		String password = s.getProperty(D2RQ.password).getLiteral().getString();

		Statement t = ls.getProperty(RML.iterator);
		String query = t.getLiteral().toString();

		RDBSource source = new RDBSource();
		source.jdbcDSN = jdbcDSN;
		source.jdbcDriver = jdbcDriver;
		source.username = username;
		source.password = password;

		// Apache jena "escapes" double quotes, so "Name" becomes \"Name\"
		// which is internally stored as \\"Name\\". We thus need to remove
		// occurrences of \\
		source.query = query.replace("\\", "");
		
		source.nulls.addAll(getNullValues(ls));

		return source;
	}

	public static LogicalSource createSPARQLCSVSource(Resource ls, String mpath) {
		SPARQLCSVSource source = new SPARQLCSVSource();
		String iterator = ls.getProperty(RML.iterator).getLiteral().getString();

		if (ls.hasProperty(RDF.type, VOID.Dataset)) {
			String file = ls.getPropertyResourceValue(VOID.dataDump).getURI();

			source.file = getAbsoluteOrRelativeFromFileProtocol(file, mpath);
			source.encoding = getEncoding(ls);
			source.iterator = iterator;
			source.nulls.addAll(getNullValues(ls));
			return source;
		} else {
			// WE HAVE A SIMPLE SPARQL SOURCE
			String file = getFile(ls);
			source.file = getAbsoluteOrRelative(file, mpath);
			source.encoding = getEncoding(ls);
			source.iterator = iterator;
			source.compression = getCompression(ls);
			source.nulls.addAll(getNullValues(ls));
			return source;
		}
	}

	// TODO: RML.SPARQL_Results_JSON
	// TODO: RML.SPARQL_Results_XML

	// *************************************************************************
	// *
	// * UTILITY FUNCTIONS FOR LOGICAL SOURCES
	// *
	// *************************************************************************

	private static String getFile(Resource ls) {
		Resource source = ls.getPropertyResourceValue(RML.source);

		if (source.hasProperty(RDF.type, RML.RelativePathSource)) {
			String file = source.getProperty(RML.path).getLiteral().getString();

			Resource root = source.getPropertyResourceValue(RML.root);
			if (root != null && !RML.MappingDirectory.equals(root)) {
				throw new RuntimeException("Root not yet implemented");
			}

			// By default BURP treats it relative to mapping.
			return file;
		}

		if (source.hasProperty(RDF.type, DCAT.Distribution)) {
			String url = source.getPropertyResourceValue(DCAT.downloadURL).getURI();
			return Util.downloadFile(url);
		}

		throw new RuntimeException("Source from other way not yet implemented");
	}

	private static Resource getCompression(Resource ls) {
		Resource r = ls.getPropertyResourceValue(RML.source);

		r = r.getPropertyResourceValue(RML.compression);
		if (r == null || RML.none.equals(r))
			return RML.none;
		if (RML.zip.equals(r))
			return RML.zip;
		if (RML.gzip.equals(r))
			return RML.gzip;
		if (RML.targz.equals(r))
			return RML.targz;
		if (RML.tarxz.equals(r))
			return RML.tarxz;

		throw new RuntimeException("Provided compression " + r + " not supported.");
	}

	private static Charset getEncoding(Resource ls) {
		Resource r = ls.getPropertyResourceValue(RML.source);

		r = r.getPropertyResourceValue(RML.encoding);
		if (r == null || RML.UTF8.equals(r))
			return StandardCharsets.UTF_8;
		if (RML.UTF16.equals(r))
			return StandardCharsets.UTF_16;

		throw new RuntimeException("Provided Character Set " + r + " not supported.");
	}

	private static String getAbsoluteOrRelative(String file, String mpath) {
		if (new File(file).isAbsolute())
			return file;
		return new File(mpath, file).getAbsolutePath();
	}

	private static String getAbsoluteOrRelativeFromFileProtocol(String file, String mpath) {
		try {
			URL url = new URL(file);
			if (Util.isAbsoluteAndValidIRI(file))
				return file;
			String abs = new File(mpath, url.getPath()).toURI().toURL().toString();
			return abs.replaceFirst("file:/", "");
		} catch (MalformedURLException e) {
			throw new RuntimeException(file + " is not an URL.");
		}
	}
	
	private static List<Object> getNullValues(Resource ls) {
		Resource r = ls.getPropertyResourceValue(RML.source);
		List<Object> os = new ArrayList<>();
		r.listProperties(RML.NULL).forEach(t -> {
			if(t.getObject().isResource()) {
				// WE ASSUME WE CAN HAVE RESOURCES AS NULL FOR 
				// SPARQL SOURCES
				os.add(t.getObject().asResource());
			} else {
				os.add(t.getObject().asLiteral().getValue());
			}
		});
		return os;
	}

}
