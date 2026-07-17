package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.util.Util;
import burp.vocabularies.RER;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class RDBSource extends LogicalSource {
    public Path currentWorkingDirectory;
    public String jdbcDriver;
    @NonNull public String jdbcDSN;
    public String password;
    public String username;
    public String query;
    public Statement queryStmt;

    protected Resource referenceFormulation;

    public final Map<String, Origin> referenceOrigins = new LinkedHashMap<>();

    @Override
    public Resource getReferenceFormulation() {
        return referenceFormulation;
    }


    @Override
    public Iterable<Iteration> iterator() {
        Connection connection = null;
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
            if (resolvedJdbcDSN.startsWith("jdbc:sqlite:") && !resolvedJdbcDSN.startsWith("jdbc:sqlite::")) {
                Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
                if (!currentWorkingDirectory.toAbsolutePath().equals(userDir)) {
                    String pathPart = resolvedJdbcDSN.substring("jdbc:sqlite:".length());
                    Path path = Paths.get(pathPart);
                    if (!path.isAbsolute()) {
                        resolvedJdbcDSN = "jdbc:sqlite:" + currentWorkingDirectory.resolve(path).toAbsolutePath();
                    }
                }
            }

            connection = DriverManager.getConnection(resolvedJdbcDSN, props);

            var statement = connection.createStatement();
            ResultSet resultset = statement.executeQuery(query);

            Map<String, Integer> indexMap = new HashMap<>();
            for (int i = 1; i <= resultset.getMetaData().getColumnCount(); i++) {
                indexMap.put(resultset.getMetaData().getColumnLabel(i), i);
            }

            final Connection finalConnection = connection;
            return () -> new Iterator<>() {
                @Override
                public boolean hasNext() {
                    try {
                        boolean goNext = resultset.next();
                        if (!goNext) {
                            resultset.close();
                            statement.close();
                            finalConnection.close();
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
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            throw new BurpException(new RmlError("Syntax error in SQL query " + query,
                    new Origin(queryStmt, StatementPart.Object),
                    RER.ReferenceFormulationSyntaxError, e));
        } catch (BurpException e) {
            throw e;
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            throw new BurpException(new RmlError("Error in RDB source. Query " + query,
                    new Origin(queryStmt, StatementPart.Object),
                    RER.LogicalSourceError, e));
        }
    }


    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        synchronized (referenceOrigins) {
            referenceOrigins.putIfAbsent(reference, origin);
        }
        return new RDBReference(reference, origin);
    }
}

class RDBReference extends Reference {
    public RDBReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof RDBIteration rdbIteration))
            throw new IllegalArgumentException("RDBReference can only be used with RDBIteration.");

        List<Object> l = new ArrayList<>();
        String columnName = StringEscapeUtils.unescapeJava(reference).trim();
        String matchedKey = getMatchedKey(rdbIteration, columnName);

        Object value = rdbIteration.values.get(matchedKey);
        if (value != null && !rdbIteration.getNulls().contains(value)) {
            l.add(value);
        }

        return l;
    }

    protected static boolean isQuoted(String columnName) {
        return (columnName.startsWith("\"") && columnName.endsWith("\""))
                || (columnName.startsWith("`") && columnName.endsWith("`"))
                || (columnName.startsWith("[") && columnName.endsWith("]"))
                || (columnName.startsWith("'") && columnName.endsWith("'"));
    }

    private @NonNull String getMatchedKey(RDBIteration rdbIteration, String columnName) {
        String matchedKey = null;
        var isQuoted = isQuoted(columnName);
        if (isQuoted) {
            // Quoted: case-sensitive match (once we removed the quotes)
            String cleanName = columnName.substring(1, columnName.length() - 1);
            if (rdbIteration.values.containsKey(cleanName)) {
                matchedKey = cleanName;
            }
        } else {
            // Unquoted: case-insensitive match
            matchedKey = rdbIteration.values.keySet().stream().filter(key -> key.equalsIgnoreCase(columnName)).findFirst().orElse(null);
        }

        if (matchedKey == null) {
            throw new BurpException(new RmlError("Attribute " + columnName + " does not exist.", origin, RER.ReferenceFormulationExecutionError));
        }
        return matchedKey;
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