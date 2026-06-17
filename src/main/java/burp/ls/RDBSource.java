package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.util.Util;
import burp.vocabularies.RER;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.jena.rdf.model.Resource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class RDBSource extends LogicalSource {
    public Path currentWorkingDirectory;
    public String jdbcDriver;
    public String jdbcDSN;
    public String password;
    public String username;
    public String query;

    protected Resource referenceFormulation;

    @Override
    public Resource getReferenceFormulation() {
        return referenceFormulation;
    }

    @Override
    public Iterable<Iteration> iterator() {
        try {
            Properties props = new Properties();
            if (username != null && !username.isEmpty()) props.setProperty("user", username);
            if (password != null && !password.isEmpty()) props.setProperty("password", password);

            if (jdbcDriver != null) {
                try {
                    Class.forName(jdbcDriver);
                } catch (ClassNotFoundException e) {
                    throw new BurpException(
                            new RmlError("Problem querying database.", null, RER.Error, e)
                    );
                }
            }

            String resolvedJdbcDSN = jdbcDSN;
            if (resolvedJdbcDSN != null && resolvedJdbcDSN.startsWith("jdbc:sqlite:") && !resolvedJdbcDSN.startsWith("jdbc:sqlite::")) {
                Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
                if (!currentWorkingDirectory.toAbsolutePath().equals(userDir)) {
                    String pathPart = resolvedJdbcDSN.substring("jdbc:sqlite:".length());
                    Path path = Paths.get(pathPart);
                    if (!path.isAbsolute()) {
                        resolvedJdbcDSN = "jdbc:sqlite:" + currentWorkingDirectory.resolve(path).toAbsolutePath();
                    }
                }
            }

            Connection connection = DriverManager.getConnection(resolvedJdbcDSN, props);
            Statement statement = connection.createStatement();
            ResultSet resultset = statement.executeQuery(query);

            Map<String, Integer> indexMap = new HashMap<>();
            for (int i = 1; i <= resultset.getMetaData().getColumnCount(); i++) {
                indexMap.put(resultset.getMetaData().getColumnLabel(i), i);
            }

            return () -> new Iterator<Iteration>() {
                @Override
                public boolean hasNext() {
                    try {
                        boolean goNext = resultset.next();
                        if (!goNext) {
                            resultset.close();
                            statement.close();
                            connection.close();
                        }
                        return goNext;
                    } catch (SQLException e) {
                        throw new BurpException(
                                new RmlError("Problem querying database while iterating over rows.", null, RER.LogicalSourceError, e)
                        );
                    }
                }

                @Override
                public Iteration next() {
                    return new RDBIteration(resultset, indexMap, getNulls());
                }
            };
        } catch (SQLSyntaxErrorException e) {
            throw new BurpException(new RmlError("Syntax error in SQL " + query, null, RER.ReferenceFormulationSyntaxError, e));
        } catch (Throwable e) {
            throw new BurpException(new RmlError("Error in RDB source.", null, RER.LogicalSourceError));
        }
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return new RDBReference(reference, origin);
    }
}

class RDBReference extends Reference {
    public RDBReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof RDBIteration rdbIteration)) {
            throw new IllegalArgumentException("RDBReference can only be used with RDBIteration.");
        }
        List<Object> l = new ArrayList<>();
        String columnname = StringEscapeUtils.unescapeJava(reference);

        if (!rdbIteration.values.containsKey(columnname) && !rdbIteration.values.containsKey(
                columnname != null ? columnname.replace("\"", "") : null)) {
            throw new BurpException(new RmlError("Attribute " + columnname + " does not exist.", origin, RER.ReferenceFormulationExecutionError));
        }

        Object value = rdbIteration.values.get(columnname);

        if (value == null) {
            value = rdbIteration.values.get(columnname != null ? columnname.replace("\"", "") : null);
        }

        if (value != null && !rdbIteration.getNulls().contains(value)) {
            l.add(value);
        }

        return l;
    }
}

class RDBIteration extends Iteration {
    public final Map<String, Object> values = new LinkedHashMap<>();

    public RDBIteration(ResultSet resultSet, Map<String, Integer> indexMap, Set<Object> nulls) {
        super(nulls);
        for (String ref : indexMap.keySet()) {
            try {
                Object o = resultSet.getObject(indexMap.get(ref));
                if (o != null) {
                    if (o instanceof byte[]) {
                        o = Util.bytesToHexString((byte[]) o);
                    }
                }
                values.put(ref, o);
            } catch (Exception e) {
                throw new RuntimeException("Error retrieving values from result set.", e);
            }
        }
    }

    @Override
    public String asString() {
        throw new UnsupportedOperationException("Not implemented. Does this make sense in the context of LV?");
    }
}