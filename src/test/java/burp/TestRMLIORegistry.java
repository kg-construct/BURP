package burp;

import burp.vocabularies.D2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Testcontainers
public class TestRMLIORegistry extends TestRMLModule {

    public static String base = "./target/test-classes/rml-io-registry/";

    @Override
    public String getBase() {
        return base;
    }

    @Override
    public void prepareSource(TestData testData) throws Exception {
        String mappingPath = new File(base + testData.ID, testData.mapping).getAbsolutePath();
        String newMappingPath = mappingPath;

        if ("application/sql".equals(testData.input_format1)) {
            System.out.println("Preparing database connection...");
            Model model = RDFDataMgr.loadModel(mappingPath);
            String jdbcDriver = model.listObjectsOfProperty(D2RQ.jdbcDriver).next().asLiteral().getString();

            String queryString = "";
            JdbcDatabaseContainer<?> db;

            switch (jdbcDriver) {
                case "com.mysql.cj.jdbc.Driver":
                    queryString = "?allowMultiQueries=true";
                    MYSQL_CONTAINER.addParameter("allowMultiQueries", "true");
                    try (var conn = java.sql.DriverManager.getConnection(MYSQL_CONTAINER.getJdbcUrl(), "root", MYSQL_CONTAINER.getPassword())) {
                        try (var stmt = conn.createStatement()) {
                            stmt.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" + MYSQL_CONTAINER.getUsername() + "'@'%'");
                            stmt.executeUpdate("FLUSH PRIVILEGES");
                            stmt.executeUpdate("DROP DATABASE IF EXISTS test");
                            stmt.executeUpdate("CREATE DATABASE test");
                        }
                    }
                    db = MYSQL_CONTAINER;
                    break;

                case "org.postgresql.Driver":
                    try (var conn = PGSQL_CONTAINER.createConnection("")) {
                        try (var stmt = conn.createStatement()) {
                            stmt.executeUpdate("DROP SCHEMA public CASCADE");
                            stmt.executeUpdate("CREATE SCHEMA public");
                            stmt.executeUpdate("GRANT ALL ON SCHEMA public TO public");
                            stmt.executeUpdate("GRANT ALL ON SCHEMA public TO postgres");
                        }
                    }
                    db = PGSQL_CONTAINER;
                    break;

                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    MSSQL_CONTAINER.withUrlParam("databaseName", "master");
                    try (var conn = MSSQL_CONTAINER.createConnection("")) {
                        try (var stmt = conn.createStatement()) {
                            stmt.executeUpdate("IF EXISTS (SELECT name FROM sys.databases WHERE name = N'TestDB') ALTER DATABASE [TestDB] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                            stmt.executeUpdate("IF EXISTS (SELECT name FROM sys.databases WHERE name = N'TestDB') DROP DATABASE [TestDB]");
                            stmt.executeUpdate("CREATE DATABASE [TestDB]");
                        }
                    }
                    MSSQL_CONTAINER.withUrlParam("databaseName", "TestDB");
                    db = MSSQL_CONTAINER;
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported JDBC driver: " + jdbcDriver);
            }

            System.out.printf("Updating database connection in mapping with JDBC URL=%s Username=%s Password=%s%n", db.getJdbcUrl(), db.getUsername(), db.getPassword());
            String sparql = String.format(
                    """
                            PREFIX d2rq: <http://www.wiwiss.fu-berlin.de/suhl/bizer/D2RQ/0.1#>
                            DELETE { ?s d2rq:jdbcDSN ?oldDSN; d2rq:username ?oldUser; d2rq:password ?oldPass }
                            INSERT { ?s d2rq:jdbcDSN "%s"; d2rq:username "%s"; d2rq:password "%s" }
                            WHERE { ?s d2rq:jdbcDSN ?oldDSN; d2rq:username ?oldUser; d2rq:password ?oldPass }""",
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()
            );
            var updateRequest = UpdateFactory.create(sparql);
            UpdateAction.execute(updateRequest, model);

            newMappingPath = Paths.get(base + testData.ID, "mapping-new.ttl").toAbsolutePath().toString();
            try (FileOutputStream out = new FileOutputStream(newMappingPath)) {
                RDFDataMgr.write(out, model, RDFFormat.TURTLE_PRETTY);
            }
            System.out.println("Mapping updated in " + newMappingPath);

            System.out.println("Populating database...");
            String sqlFilePath = new File(base + testData.ID, testData.input1).getAbsolutePath();
            String sql = new String(Files.readAllBytes(Paths.get(sqlFilePath)));
            try (var conn = db.createConnection(queryString)) {
                try (var stmt = conn.createStatement()) {
                    switch (jdbcDriver) {
                        case "org.postgresql.Driver" -> {
                            // Strip USE statements for PostgreSQL
                            sql = sql.replaceAll("(?i)\\bUSE\\s+[a-zA-Z0-9_]+\\s*;?", "");
                            // Strip database prefix qualifiers (e.g., TestDB.Student -> Student)
                            sql = sql.replaceAll("(?i)\\b(TestDB|test|testdb)\\.", "");
                        }
                        case "com.mysql.cj.jdbc.Driver" -> {
                            // Dynamically create any databases in USE statements for MySQL
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)\\bUSE\\s+([a-zA-Z0-9_]+)");
                            java.util.regex.Matcher m = p.matcher(sql);
                            while (m.find()) {
                                String dbName = m.group(1);
                                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
                            }
                        }
                        case "com.microsoft.sqlserver.jdbc.SQLServerDriver" -> {
                            // Dynamically create any databases in USE statements for SQL Server
                            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)\\bUSE\\s+([a-zA-Z0-9_]+)");
                            java.util.regex.Matcher m = p.matcher(sql);
                            while (m.find()) {
                                String dbName = m.group(1);
                                stmt.executeUpdate("IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'" + dbName + "') CREATE DATABASE [" + dbName + "]");
                            }
                        }
                    }
                    int update = stmt.executeUpdate(sql);
                    if (update > 0) {
                        System.out.println("SQL update successfully.");
                    }
                }
            }
        }

        if (!newMappingPath.equals(mappingPath)) {
            testData.mapping = Path.of(newMappingPath).getFileName().toString();
        }
    }

    @Container
    public static PostgreSQLContainer PGSQL_CONTAINER = new PostgreSQLContainer("postgres:latest")
            .withUsername("postgres")
            .withPassword("test")
            .withReuse(true);

    @Container
    public static MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:latest")
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withCommand("mysqld", "--sql_mode=ANSI_QUOTES")
            .withReuse(true);


    @Container
    public static MSSQLServerContainer MSSQL_CONTAINER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2025-latest")
                    .acceptLicense()
                    .withReuse(true);

}
