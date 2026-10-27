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
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

public class RDBSource extends LogicalSource {
    public Path mappingDirectory;
    public Path currentWorkingDirectory;
    @Nullable
    public String jdbcDriver;
    @NonNull
    public String jdbcDSN;
    @Nullable
    public String password;
    @Nullable
    public String username;
    @NonNull
    public String query;

    protected Resource referenceFormulation;

    public final Map<String, Origin> referenceOrigins = new LinkedHashMap<>();

    @Override
    public Resource getReferenceFormulation() {
        return referenceFormulation;
    }


    public Connection createConnection() throws SQLException, ClassNotFoundException {
        Properties props = new Properties();
        if (username != null && !username.isEmpty()) props.setProperty("user", username);
        if (password != null && !password.isEmpty()) props.setProperty("password", password);

        if (jdbcDriver != null) {
            Class.forName(jdbcDriver);
        }

        String resolvedJdbcDSN = jdbcDSN;
        if (resolvedJdbcDSN != null && resolvedJdbcDSN.startsWith("jdbc:sqlite:") && !resolvedJdbcDSN.startsWith("jdbc:sqlite::")) {
            String pathPart = resolvedJdbcDSN.substring("jdbc:sqlite:".length());
            Path path = Paths.get(pathPart);
            if (!path.isAbsolute()) {
                Path absolutePath = getAbsolutePath(path);
                if (absolutePath != null) {
                    resolvedJdbcDSN = "jdbc:sqlite:" + absolutePath;
                }
            }
        }

        return DriverManager.getConnection(resolvedJdbcDSN, props);
    }

    private @Nullable Path getAbsolutePath(Path path) {
        Path absolutePath = null;
        if (mappingDirectory != null && Files.exists(mappingDirectory.resolve(path))) {
            absolutePath = mappingDirectory.resolve(path).toAbsolutePath();
        } else if (currentWorkingDirectory != null && Files.exists(currentWorkingDirectory.resolve(path))) {
            absolutePath = currentWorkingDirectory.resolve(path).toAbsolutePath();
        } else if (mappingDirectory != null) {
            absolutePath = mappingDirectory.resolve(path).toAbsolutePath();
        } else if (currentWorkingDirectory != null) {
            absolutePath = currentWorkingDirectory.resolve(path).toAbsolutePath();
        }
        return absolutePath;
    }

    @Override
    public Iterable<Iteration> iterator() {
        Connection connection = null;
        Origin queryOrigin = emitOrigin("query");
        try {
            connection = createConnection();

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
                                new RmlError("Problem querying database while iterating over rows.",
                                        queryOrigin, RER.LogicalSourceError, e, RDBSource.this)
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
                    queryOrigin, RER.ReferenceFormulationSyntaxError, e, this));
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
                    queryOrigin, RER.LogicalSourceError, e, this));
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
            throw new BurpException(new RmlError("Attribute " + columnName + " does not exist.", emitOrigin("reference"), RER.ReferenceFormulationExecutionError, this));
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