package burp.dev.rml.service.schema;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.completion.ReferenceSchemaResolver.SchemaInfo;
import burp.dev.rml.service.cache.DevServerCache;
import burp.ls.RDBSource;
import burp.model.AbstractLogicalSource;
import burp.vocabularies.Rml;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy interface implementation for extracting schema information and candidate column
 * references from SQL / relational database logical sources (RDBSource).
 */
@NullMarked
public class SqlSchemaProvider implements SchemaProvider {

    public enum ErrorKind {
        CONNECTION_ERROR,
        QUERY_ERROR
    }

    public record SqlSourceError(
            ErrorKind kind,
            String message,
            @Nullable Exception cause
    ) {
    }

    public record SqlDialectRules(
            boolean storesUpperCase,
            boolean storesLowerCase,
            boolean storesMixedCase,
            String quoteString
    ) {
        public static final SqlDialectRules DEFAULT = new SqlDialectRules(false, false, true, "\"");

        public boolean matches(String reference, Set<String> candidates) {
            if (candidates.contains(reference)) {
                return true;
            }
            String trimmed = reference.trim();
            if (isQuotedIdentifier(trimmed, quoteString)) {
                String unquoted = stripQuotes(trimmed, quoteString);
                // Quoted identifiers are case-sensitive
                return candidates.contains(unquoted);
            }

            // Unquoted identifiers match case-insensitively
            for (String c : candidates) {
                if (c.equalsIgnoreCase(trimmed)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isQuotedIdentifier(String s, String quote) {
            if (!quote.isBlank() && !quote.equals(" ")) {
                if (s.startsWith(quote) && s.endsWith(quote) && s.length() >= quote.length() * 2) {
                    return true;
                }
            }
            return (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
                    || (s.startsWith("`") && s.endsWith("`") && s.length() >= 2)
                    || (s.startsWith("[") && s.endsWith("]") && s.length() >= 2)
                    || (s.startsWith("'") && s.endsWith("'") && s.length() >= 2);
        }

        private static String stripQuotes(String s, String quote) {
            String trimmed = s.trim();
            if (!quote.isBlank() && !quote.equals(" ")) {
                if (trimmed.startsWith(quote) && trimmed.endsWith(quote) && trimmed.length() >= quote.length() * 2) {
                    return trimmed.substring(quote.length(), trimmed.length() - quote.length());
                }
            }
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("`") && trimmed.endsWith("`"))
                    || (trimmed.startsWith("[") && trimmed.endsWith("]"))
                    || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
            return trimmed;
        }
    }

    public record CachedSchema(
            SchemaInfo schemaInfo,
            SqlDialectRules dialectRules,
            @Nullable SqlSourceError error,
            long timestamp
    ) {
    }

    private static final long ERROR_CACHE_TTL_MS = 60_000; // 1 minute
    private final @Nullable DevServerCache devServerCache;
    private final Map<String, CachedSchema> localCache = new ConcurrentHashMap<>();

    public SqlSchemaProvider() {
        this(null);
    }

    public SqlSchemaProvider(@Nullable DevServerCache devServerCache) {
        this.devServerCache = devServerCache;
    }

    @Override
    public boolean supports(@Nullable AbstractLogicalSource logicalSource) {
        return logicalSource instanceof RDBSource;
    }

    private @Nullable CachedSchema getCached(String key) {
        if (devServerCache != null) {
            return devServerCache.getSqlSchema(key);
        }
        return localCache.get(key);
    }

    private void putCached(String key, CachedSchema schema) {
        if (devServerCache != null) {
            devServerCache.putSqlSchema(key, schema);
        } else {
            localCache.put(key, schema);
        }
    }

    private void removeCached(String key) {
        if (devServerCache != null) {
            devServerCache.removeSqlSchema(key);
        } else {
            localCache.remove(key);
        }
    }

    public @Nullable SqlSourceError getError(RDBSource source) {
        String cacheKey = getCacheKey(source);
        CachedSchema cached = getCached(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.error() != null && cached.error().kind() == ErrorKind.CONNECTION_ERROR) {
            if (System.currentTimeMillis() - cached.timestamp() >= ERROR_CACHE_TTL_MS) {
                removeCached(cacheKey);
                return null;
            }
        }
        return cached.error();
    }

    public SqlDialectRules getDialectRules(RDBSource source) {
        CachedSchema cached = getCached(getCacheKey(source));
        return cached != null ? cached.dialectRules() : SqlDialectRules.DEFAULT;
    }

    public void invalidateCache() {
        if (devServerCache != null) {
            devServerCache.invalidateAll();
        }
        localCache.clear();
    }

    @Override
    public SchemaInfo extractSchema(AbstractLogicalSource logicalSource) {
        if (!(logicalSource instanceof RDBSource rdbSource)) {
            return new SchemaInfo(Rml.SQL2008Query, Collections.emptyMap(), Collections.emptyMap(), false);
        }

        String cacheKey = getCacheKey(rdbSource);
        CachedSchema cached = getCached(cacheKey);
        if (cached != null) {
            if (cached.error() != null && cached.error().kind() == ErrorKind.CONNECTION_ERROR) {
                if (System.currentTimeMillis() - cached.timestamp() < ERROR_CACHE_TTL_MS) {
                    return cached.schemaInfo();
                }
                removeCached(cacheKey);
            } else {
                return cached.schemaInfo();
            }
        }

        String refFormulation = rdbSource.getReferenceFormulation() != null
                ? rdbSource.getReferenceFormulation().getURI()
                : Rml.SQL2008Query;

        Map<String, String> candidateValues = new LinkedHashMap<>();
        Map<String, ReferenceCompletionOrigin> candidateOrigins = new LinkedHashMap<>();
        boolean hasKnownSchemaOrData = false;
        SqlDialectRules dialectRules = SqlDialectRules.DEFAULT;
        SqlSourceError sourceError = null;

        String query = rdbSource.query;
        if (query == null || query.isBlank()) {
            SchemaInfo emptyInfo = new SchemaInfo(refFormulation, candidateValues, candidateOrigins, false);
            putCached(cacheKey, new CachedSchema(emptyInfo, dialectRules, null, System.currentTimeMillis()));
            return emptyInfo;
        }

        Connection connection;
        try {
            connection = rdbSource.createConnection();
        } catch (Exception e) {
            sourceError = new SqlSourceError(ErrorKind.CONNECTION_ERROR, "Cannot connect to database: " + e.getMessage(), e);
            SchemaInfo info = new SchemaInfo(refFormulation, candidateValues, candidateOrigins, false);
            putCached(cacheKey, new CachedSchema(info, dialectRules, sourceError, System.currentTimeMillis()));
            return info;
        }

        try (connection) {
            // 1. Inspect dialect rules from DatabaseMetaData
            try {
                DatabaseMetaData dbMeta = connection.getMetaData();
                if (dbMeta != null) {
                    dialectRules = new SqlDialectRules(
                            dbMeta.storesUpperCaseIdentifiers(),
                            dbMeta.storesLowerCaseIdentifiers(),
                            dbMeta.storesMixedCaseIdentifiers(),
                            dbMeta.getIdentifierQuoteString()
                    );
                }
            } catch (Exception ignored) {
            }

            String ptrStr = rdbSource.getSourcePointers().stream()
                    .map(PlanNodeDescriptor::getRdfPointerString)
                    .findFirst()
                    .orElse(null);

            // 2. Extract column metadata and optional sample row via setMaxRows(1)
            ResultSetMetaData rsMeta;
            Map<String, String> sampleRowValues = new HashMap<>();

            try (Statement stmt = connection.createStatement()) {
                stmt.setMaxRows(1);

                try (ResultSet rs = stmt.executeQuery(query)) {
                    rsMeta = rs.getMetaData();
                    if (rs.next()) {
                        int colCount = rsMeta.getColumnCount();
                        for (int i = 1; i <= colCount; i++) {
                            String label = rsMeta.getColumnLabel(i);
                            if (label == null || label.isBlank()) {
                                label = rsMeta.getColumnName(i);
                            }
                            Object val = rs.getObject(i);
                            if (val != null) {
                                sampleRowValues.put(label, val.toString());
                            }
                        }
                    }

                    if (rsMeta != null) {
                        int colCount = rsMeta.getColumnCount();
                        for (int i = 1; i <= colCount; i++) {
                            String label = rsMeta.getColumnLabel(i);
                            if (label == null || label.isBlank()) {
                                label = rsMeta.getColumnName(i);
                            }
                            String typeName = rsMeta.getColumnTypeName(i);
                            String sample = sampleRowValues.getOrDefault(label, typeName != null ? typeName : "");
                            candidateValues.put(label, sample);
                            candidateOrigins.put(label, new ReferenceCompletionOrigin("sql", null, rdbSource.jdbcDSN, ptrStr));
                        }
                        hasKnownSchemaOrData = !candidateValues.isEmpty();
                    }
                }
            }
        } catch (SQLException e) {
            sourceError = new SqlSourceError(ErrorKind.QUERY_ERROR, "SQL error in query: " + e.getMessage(), e);
        } catch (Exception e) {
            sourceError = new SqlSourceError(ErrorKind.QUERY_ERROR, "Error executing SQL query: " + e.getMessage(), e);
        }

        SchemaInfo resultInfo = new SchemaInfo(refFormulation, candidateValues, candidateOrigins, hasKnownSchemaOrData);
        putCached(cacheKey, new CachedSchema(resultInfo, dialectRules, sourceError, System.currentTimeMillis()));
        return resultInfo;
    }

    private String getCacheKey(RDBSource source) {
        String dsn = source.jdbcDSN != null ? source.jdbcDSN : "";
        String q = source.query != null ? source.query : "";
        return dsn + "::" + q;
    }
}
