package burp;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.opencsv.CSVReader;

import net.minidev.json.JSONObject;

public abstract class LogicalSource {

	protected abstract Iterator<Iteration> iterator();

}

class CSVSource extends LogicalSource {
	
	private List<Iteration> iterations = null;
	public String file;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			if(iterations == null) {
				iterations = new ArrayList<Iteration>();
				
				FileReader fr = new FileReader(file); 
				CSVReader reader = new CSVReader(fr); 
				List<String[]> all = reader.readAll();
				reader.close();
				
				String[] header = all.remove(0);
				for(String[] rec : all) {
					iterations.add(new CSVIteration(header, rec));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}

class JSONSource extends LogicalSource {
	
	private List<Iteration> iterations = null;
	public String file;
	public String iterator;
	
	private static Configuration c = Configuration
			.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonProvider())
            .build()
            .addOptions(Option.ALWAYS_RETURN_LIST)
			;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			if(iterations == null) {
				iterations = new ArrayList<Iteration>();
				String contents = Files.readString(Paths.get(file));

				List<Map<String, Object>> nodes = JsonPath.using(c).parse(contents).read(iterator);
				for(Map<String,Object> n: nodes) {
					iterations.add(new JSONIteration(JSONObject.toJSONString(n)));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}

class RDBSource extends LogicalSource {
	
	private List<Iteration> iterations = null;

	public String jdbcDriver;
	public String jdbcDSN;
	public String password;
	public String username;
	public String query;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			if(iterations == null) {
				iterations = new ArrayList<Iteration>();

				Properties props = new Properties();
				if(username != null && !"".equals(username))
					props.setProperty("user", username);
				if(password != null && !"".equals(password))
					props.setProperty("password", password);			
				
				Connection connection = DriverManager.getConnection(jdbcDSN, props);
				Statement statement = connection.createStatement();
				final ResultSet resultset = statement.executeQuery(query);
				
				Map<String, Integer> indexMap = new HashMap<String, Integer>();
				for(int i = 1; i <= resultset.getMetaData().getColumnCount(); i++) {
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
						return new RDBIteration(resultset, indexMap);
					}
					
				};
				
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}