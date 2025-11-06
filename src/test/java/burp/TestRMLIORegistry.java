package burp;

import burp.vocabularies.D2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRMLIORegistry extends TestRMLModule {
    public static String base = "./src/test/resources/rml-io-registry/";

    @Override
    public String getBase() {
        return base;
    }

    static PostgreSQLContainer<?> PGSQL_CONTAINER = new PostgreSQLContainer<>("postgres:latest")
            .withUsername("postgres")
            .withPassword("test");
    static private CompletableFuture<Void> PGSQL_CONTAINER_FUTURE = null;

    static MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8")
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withCommand("mysqld", "--sql_mode=ANSI_QUOTES");
    static private CompletableFuture<Void> MYSQL_CONTAINER_FUTURE = null;

    static MSSQLServerContainer<?> MSSQL_CONTAINER = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04")
            .acceptLicense();
    static private CompletableFuture<Void> MSSQL_CONTAINER_FUTURE = null;

    @BeforeAll
    static void startContainers() {
        PGSQL_CONTAINER_FUTURE = CompletableFuture.runAsync(PGSQL_CONTAINER::start);
        MYSQL_CONTAINER_FUTURE = CompletableFuture.runAsync(MYSQL_CONTAINER::start);
        MSSQL_CONTAINER_FUTURE = CompletableFuture.runAsync(MSSQL_CONTAINER::start);
    }

    @AfterAll
    static void stopContainers() {
        Stream.of(PGSQL_CONTAINER, MYSQL_CONTAINER, MSSQL_CONTAINER).parallel().forEach(GenericContainer::stop);
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    void testDirectoryBasedCases(TestData testData) throws Exception {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Processing test %s%n", testData.ID);
        System.out.println("--------------------------------------------------------------------------------");

        String mappingPath = new File(base + testData.ID, testData.mapping).getAbsolutePath();
        String newMappingPath = mappingPath;

        if (Objects.equals(testData.input_format1, "application/sql")) {
            System.out.println("Preparing mapping for database connection...");
            Model model = RDFDataMgr.loadModel(mappingPath);
            String jdbcDriver = model.listObjectsOfProperty(D2RQ.jdbcDriver).next().asLiteral().getString();

            String queryString = "";
            JdbcDatabaseContainer<?> db = switch (jdbcDriver) {
                case "com.mysql.cj.jdbc.Driver" -> {
                    MYSQL_CONTAINER_FUTURE.join();
                    queryString = "?allowMultiQueries=true";
                    MYSQL_CONTAINER.addParameter("allowMultiQueries", "true");
                    yield MYSQL_CONTAINER;
                }
                case "org.postgresql.Driver" -> {
                    PGSQL_CONTAINER_FUTURE.join();
                    yield PGSQL_CONTAINER;
                }
                case "com.microsoft.sqlserver.jdbc.SQLServerDriver" -> {
                    MSSQL_CONTAINER_FUTURE.join();
                    MSSQL_CONTAINER.withUrlParam("databaseName", "master");
                    try (var conn = MSSQL_CONTAINER.createConnection("");
                         var stmt = conn.createStatement()) {
                        stmt.executeUpdate("IF EXISTS (SELECT name FROM sys.databases WHERE name = N'TestDB') ALTER DATABASE [TestDB] SET  SINGLE_USER WITH ROLLBACK IMMEDIATE");
                        stmt.executeUpdate("IF EXISTS (SELECT name FROM sys.databases WHERE name = N'TestDB') DROP DATABASE [TestDB]");
                        stmt.executeUpdate("CREATE DATABASE [TestDB]");
                    }
                    MSSQL_CONTAINER.withUrlParam("databaseName", "TestDB");
                    yield MSSQL_CONTAINER;
                }
                default -> throw new IllegalArgumentException("Unsupported JDBC driver: " + jdbcDriver);
            };

            String sparql = String.format(
                    """
                            PREFIX d2rq: <http://www.wiwiss.fu-berlin.de/suhl/bizer/D2RQ/0.1#>
                            DELETE { ?s d2rq:jdbcDSN ?oldDSN; d2rq:username ?oldUser; d2rq:password ?oldPass }
                            INSERT { ?s d2rq:jdbcDSN "%s"; d2rq:username "%s"; d2rq:password "%s" }
                            WHERE { ?s d2rq:jdbcDSN ?oldDSN; d2rq:username ?oldUser; d2rq:password ?oldPass }""",
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()
            );
            UpdateRequest updateRequest = UpdateFactory.create(sparql);
            UpdateAction.execute(updateRequest, model);

            newMappingPath = Paths.get(base + testData.ID, "mapping-new.ttl").toAbsolutePath().toString();
            try (FileOutputStream out = new FileOutputStream(newMappingPath)) {
                RDFDataMgr.write(out, model, RDFFormat.TURTLE_PRETTY);
            }

            System.out.println("Populating database...");
            String sqlFilePath = new File(base + testData.ID, testData.input1).getAbsolutePath();
            String sql = new String(Files.readAllBytes(Paths.get(sqlFilePath)));
            try (var conn = db.createConnection(queryString);
                 var stmt = conn.createStatement()) {
                int update = stmt.executeUpdate(sql);
                if (update > 0) {
                    System.out.println("SQL update successfully.");
                }
            }

        }

        System.out.println(testData.mapping);
        System.out.println(testData.output1);
        System.out.println(testData.error);
        System.out.println();

        if (testData.error)
            testForNotOK(testData, newMappingPath);
        else
            testForOK(testData, newMappingPath);
    }


    public void testForOK(TestData testData, String mappingPath) throws IOException {
        String r = Files.createTempFile(null, ".nq").toString();
        System.out.printf("Writing output to %s%n", r);

        System.out.println("This test should generate a graph.");
        String o = new File(base + testData.ID, testData.output1).getAbsolutePath();

        int exit = Main.doMain(new String[]{"-m", mappingPath, "-o", r, "-b", "http://example.com/"});

        Model expected = RDFDataMgr.loadModel(o);
        Model actual = RDFDataMgr.loadModel(r);

        if (!expected.isIsomorphicWith(actual)) {
            expected.write(System.out, "Turtle");
            System.out.println("---");
            actual.write(System.out, "Turtle");
        }

        assertEquals(0, exit);

        System.out.println(expected.isIsomorphicWith(actual) ? "OK" : "NOK");

        assertTrue(expected.isIsomorphicWith(actual));
    }

    public void testForNotOK(TestData testData, String mappingPath) throws IOException {
        String r = Files.createTempFile(null, ".nq").toString();
        System.out.printf("Writing output to %s%n", r);

        System.out.println("This test should NOT generate a graph.");
        int exit = Main.doMain(new String[]{"-m", mappingPath, "-o", r});
        Path path = Paths.get(r);
        System.out.println(Files.size(path) == 0 ? "OK" : "NOK");
        Model actual = RDFDataMgr.loadModel(r);
        actual.write(System.out, "NQ");

        assertTrue(exit > 0);
        assertEquals(0, Files.size(path));

        System.out.println();
    }
}
