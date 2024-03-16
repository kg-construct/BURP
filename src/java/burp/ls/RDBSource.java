package burp.ls;

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
import java.util.Set;

import org.apache.commons.text.StringEscapeUtils;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.util.Util;

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

class RDBIteration extends Iteration {

	private Map<String, Object> values = new HashMap<String, Object>();

	public RDBIteration(ResultSet resultSet, Map<String, Integer> indexMap, Set<Object> nulls) {
		super(nulls);
		
		for(String ref : indexMap.keySet()) {
			try {
				Object o = resultSet.getObject(indexMap.get(ref));
				if(o != null) {
					if(o instanceof byte[]) {
						o = Util.bytesToHexString((byte[]) o);
					}
				}
				
				values.put(ref, o);
			} catch (Exception e) {
				throw new RuntimeException("Error retrieving values from result set.");
			}
		}
	}
	
	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		String columnname = StringEscapeUtils.unescapeJava(reference);		
		
		if(!values.containsKey(columnname) && !values.containsKey(columnname.replace("\"", "")))
			throw new RuntimeException("Attribute " + columnname + " does not exist.");
		
		Object value = values.get(columnname);
		
		// Check whether the user added the right column names in the mappings
		if(value == null)
			// Now try without quotes
			value = values.get(columnname.replace("\"", ""));
				
		if(value != null && !nulls.contains(value))
			l.add(value);
		
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		for(Object o : getValuesFor(reference))
			if(o != null)
				l.add(o.toString());
		return l;
	}
	
}