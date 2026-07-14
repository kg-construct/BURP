package burp;

import burp.vocabularies.D2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Disabled
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
                    MYSQL_CONTAINER_FUTURE.join();
                    queryString = "?allowMultiQueries=true";
                    MYSQL_CONTAINER.addParameter("allowMultiQueries", "true");
                    db = MYSQL_CONTAINER;
                    break;

                case "org.postgresql.Driver":
                    PGSQL_CONTAINER_FUTURE.join();
                    db = PGSQL_CONTAINER;
                    break;

                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    MSSQL_CONTAINER_FUTURE.join();
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

    public static PostgreSQLContainer PGSQL_CONTAINER = new PostgreSQLContainer("postgres:latest")
            .withUsername("postgres")
            .withPassword("test");
    private static CompletableFuture<Void> PGSQL_CONTAINER_FUTURE = null;

    public static MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:8")
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withCommand("mysqld", "--sql_mode=ANSI_QUOTES");
    private static CompletableFuture<Void> MYSQL_CONTAINER_FUTURE = null;

    public static MSSQLServerContainer MSSQL_CONTAINER = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
            .acceptLicense();
    private static CompletableFuture<Void> MSSQL_CONTAINER_FUTURE = null;

    @BeforeAll
    public static void startContainers() {
        PGSQL_CONTAINER_FUTURE = CompletableFuture.runAsync(PGSQL_CONTAINER::start);
        MYSQL_CONTAINER_FUTURE = CompletableFuture.runAsync(MYSQL_CONTAINER::start);
        MSSQL_CONTAINER_FUTURE = CompletableFuture.runAsync(MSSQL_CONTAINER::start);
    }

    @AfterAll
    public static void stopContainers() {
        Stream.of(PGSQL_CONTAINER, MYSQL_CONTAINER, MSSQL_CONTAINER)
                .parallel()
                .forEach(JdbcDatabaseContainer::stop);
    }
}
