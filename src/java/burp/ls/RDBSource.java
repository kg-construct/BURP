package burp.ls;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import burp.iteration.RDBIteration;
import burp.model.Iteration;
import burp.model.LogicalSource;

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