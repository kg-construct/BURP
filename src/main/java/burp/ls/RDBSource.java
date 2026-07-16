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

            // Validate references against the database query
            List<Map.Entry<String, Origin>> entries = new ArrayList<>(referenceOrigins.entrySet());
            //validateReferences(connection, entries);

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

    /**
     * Validates that all references (columns) used in the RML mapping exist and are unambiguous
     * in the logical table query.
     * <p>
     * Rather than executing the mapping query and checking columns on empty result sets or trying
     * to parse dialect-specific SQL string patterns, this method compiles a lightweight schema-only
     * check query {@code SELECT Ref1, Ref2 ... FROM (query) AS val_sub WHERE 1=0} on the database engine.
     * This forces the database engine to natively validate the references, catching:
     * <ul>
     *     <li>Non-existent column references (even if the underlying tables are empty).</li>
     *     <li>Ambiguous or duplicate column names resulting from unaliased joins.</li>
     *     <li>Case-sensitivity/folding conflicts according to standard SQL rules.</li>
     * </ul>
     * If validation fails, it tests references individually to identify and report the precise
     * invalid/ambiguous column.
     *
     * @param connection the active database connection
     * @param entries the list of column references to validate
     * @throws BurpException if a reference is missing/ambiguous, or if a database query error occurs
     */
    private void validateReferences(Connection connection, List<Map.Entry<String, Origin>> entries) throws BurpException {
        if (entries.isEmpty()) {
            return;
        }

        String cleanQuery = query.trim();
        if (cleanQuery.endsWith(";")) {
            cleanQuery = cleanQuery.substring(0, cleanQuery.length() - 1);
        }

        // Use SELECT * to let the DB resolve the schema without dialect-specific column injection
        String valQuery = "SELECT * FROM (" + cleanQuery + ") AS val_sub WHERE 1=0";

        try (var valStmt = connection.createStatement();
             ResultSet rs = valStmt.executeQuery(valQuery)) {

            // Fetch the column names exactly as the database exposes them
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<String> availableColumns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                availableColumns.add(metaData.getColumnLabel(i));
            }

            // Validate against the metadata using the same logic as RDBReference
            for (Map.Entry<String, Origin> entry : entries) {
                String refName = StringEscapeUtils.unescapeJava(entry.getKey()).trim();
                boolean isQuoted = RDBReference.isQuoted(refName);
                String match = null;

                if (isQuoted) {
                    // Case-sensitive exact match
                    String cleanName = refName.substring(1, refName.length() - 1);
                    if (availableColumns.contains(cleanName)) {
                        match = cleanName;
                    }
                } else {
                    // Case-insensitive match
                    match = availableColumns.stream()
                            .filter(col -> col.equalsIgnoreCase(refName))
                            .findFirst()
                            .orElse(null);
                }

                if (match == null) {
                    throw new BurpException(new RmlError(
                            "Attribute " + entry.getKey() + " does not exist or is ambiguous.",
                            entry.getValue(),
                            RER.ReferenceFormulationExecutionError
                    ));
                }
            }

        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
            throw new BurpException(new RmlError(
                    "Problem querying database during reference validation. Query: " + valQuery,
                    null,
                    RER.LogicalSourceError,
                    e
            ));
        }
    }
    private void validateReferencesRewrite(Connection connection, List<Map.Entry<String, Origin>> entries) throws BurpException {
        if (entries.isEmpty()) {
            return;
        }

        StringBuilder valQuery = new StringBuilder("SELECT ");
        int count = 0;
        for (Map.Entry<String, Origin> entry : entries) {
            if (count > 0) valQuery.append(", ");
            String unescaped = StringEscapeUtils.unescapeJava(entry.getKey());
            valQuery.append(unescaped);
            count++;
        }
        String cleanQuery = query.trim();
        if (cleanQuery.endsWith(";")) {
            cleanQuery = cleanQuery.substring(0, cleanQuery.length() - 1);
        }
        valQuery.append(" FROM (").append(cleanQuery).append(") AS val_sub WHERE 1=0");

        try (var valStmt = connection.createStatement()) {
            valStmt.execute(valQuery.toString());
        } catch (SQLException e) {
            // One or more columns failed validation, check them individually for precise error reporting
            for (Map.Entry<String, Origin> entry : entries) {
                String unescaped = StringEscapeUtils.unescapeJava(entry.getKey());
                String testQuery = "SELECT " + unescaped + " FROM (" + cleanQuery + ") AS val_sub WHERE 1=0";
                try (var testStmt = connection.createStatement()) {
                    testStmt.execute(testQuery);
                } catch (SQLException ex) {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {}
                    throw new BurpException(new RmlError("Attribute " + entry.getKey() + " does not exist or is ambiguous.", entry.getValue(), RER.ReferenceFormulationExecutionError, ex));
                }
            }
            try {
                connection.close();
            } catch (SQLException ignored) {}
            throw new BurpException(new RmlError("Problem querying database during reference validation. Query: " + valQuery, null, RER.LogicalSourceError, e));
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