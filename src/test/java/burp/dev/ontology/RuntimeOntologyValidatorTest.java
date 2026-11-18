package burp.dev.ontology;

import burp.dev.DevServer;
import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.cache.DevServerCache;
import burp.dev.rml.service.eval.RangeDataContext;
import burp.dev.rml.service.eval.RmlEvaluator;
import burp.dev.rml.service.validator.*;
import burp.model.AbstractLogicalSource;
import burp.model.Iteration;
import burp.model.IterationId;
import burp.model.MappingDocument;
import burp.model.OriginLineage;
import burp.model.RdfPredicateObject;
import burp.model.rdf.IRITerm;
import burp.parse.ValidationMode;
import burp.processor.EvaluatedStatementContext;
import burp.processor.ExecutionOptions;
import burp.processor.RmlProcessor;
import burp.reporting.PointRange;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
public class RuntimeOntologyValidatorTest {

    private static final String DIR = "src/test/resources/burp/ontology/";

    private RmlDevServerImpl server = new RmlDevServerImpl();

    @BeforeEach
    public void setUp() {
        server = new RmlDevServerImpl();
    }

    @AfterEach
    public void tearDown() {
        server.shutdown();
    }

    @Test
    public void testDynamicDomainMismatch() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_domain_mismatch.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();

        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyDomainMismatch.getURI().equals(d.errorTypeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("ex:worksAt");
    }

    @Test
    public void testDynamicTermTypeMismatch() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_term_type_mismatch.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();

        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyTermTypeMismatch.getURI().equals(d.errorTypeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("ex:treatedBy");
    }

    @Test
    public void testDynamicIllTypedLiteral() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_ill_typed_literal.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();

        List<RmlErrorDto> diagnostics = server.validate(new ValidateParams(uri)).get();

        assertThat(diagnostics)
                .filteredOn(d -> RER.OntologyDatatypeMismatch.getURI().equals(d.errorTypeUri()))
                .hasSize(1);
        assertThat(diagnostics.getFirst().message())
                .contains("abc")
                .contains("xsd:integer");
    }

    @Test
    public void testStaticConstantPairNotDuplicatedByRuntimeValidator() throws Exception {
        Path mappingFile = Path.of(DIR, "static_domain_mismatch.ttl").toAbsolutePath();
        String uri = mappingFile.toUri().toString();
        MappingWorkspace workspace = new MappingWorkspace();
        MappingWorkspace.LoadedMapping loaded = workspace.loadMappingDetailed(uri, ValidationMode.LENIENT);
        ValidationContext ctx = new ValidationContext(loaded, uri, workspace);

        List<RmlError> staticDiags = new OntologyValidator().validate(ctx);
        List<RmlError> runtimeDiags = new RuntimeOntologyValidator().validate(ctx);

        assertThat(staticDiags).isNotEmpty();
        assertThat(runtimeDiags).isEmpty();
    }

    @Test
    public void testRangeDataContextStopsAtEndIndex() {
        AtomicInteger iteratedCount = new AtomicInteger(0);
        AbstractLogicalSource lazySource = new AbstractLogicalSource() {
            @Override
            public org.apache.jena.rdf.model.Resource getReferenceFormulation() {
                return burp.vocabularies.RML.CSV;
            }

            @Override
            public burp.model.Reference buildExportedReference(String reference, burp.reporting.Origin origin) {
                return null;
            }

            @Override
            public Iterable<Iteration> iterator() {
                return () -> new Iterator<>() {
                    int idx = 0;

                    @Override
                    public boolean hasNext() {
                        return idx < 1000;
                    }

                    @Override
                    public Iteration next() {
                        iteratedCount.incrementAndGet();
                        int cur = idx++;
                        return new Iteration(Collections.emptySet()) {
                            @Override
                            public String asString() {
                                return "row-" + cur;
                            }
                        };
                    }
                };
            }
        };

        RmlEvaluator evaluator = new RmlEvaluator(new DevServerCache());
        List<Iteration> sampled = evaluator.resolveIterations(new RangeDataContext(0, 2), lazySource);

        assertThat(sampled).hasSize(3);
        assertThat(iteratedCount.get()).isEqualTo(3);
    }

    @Test
    public void testRmlProcessorStatementObserverCapturesEvaluatedPairs() throws Exception {
        Path mappingFile = Path.of(DIR, "dynamic_domain_mismatch.ttl").toAbsolutePath();
        MappingDocument doc = new MappingWorkspace()
                .loadMappingDetailed(mappingFile.toUri().toString(), ValidationMode.LENIENT)
                .document();

        List<EvaluatedStatementContext> observed = new ArrayList<>();
        ExecutionOptions options = ExecutionOptions.builder()
                .statementObserver(observed::add)
                .build();

        new RmlProcessor().executeToStatements(doc, options);

        assertThat(observed).isNotEmpty();
        assertThat(observed).allSatisfy(ctx -> {
            assertThat(ctx.triplesMap()).isNotNull();
            assertThat(ctx.predicateObjectMap()).isNotNull();
            assertThat(ctx.predicateMap()).isNotNull();
            assertThat(ctx.objectMap()).isNotNull();
            assertThat(ctx.evaluationResult()).isNotNull();
        });
    }

    @Test
    public void testRmlErrorDtoGsonRoundTrip() {
        GsonBuilder builder = new GsonBuilder();
        DevServer.configureGson(builder);
        Gson gson = builder.create();

        IterationId iterId = new IterationId("src", 2);
        OriginLineage lineage = new OriginLineage(iterId, List.of());
        RdfPredicateObject po = new RdfPredicateObject(
                new IRITerm("http://example.org/age"),
                new burp.model.rdf.LiteralTerm("-5", new IRITerm("http://www.w3.org/2001/XMLSchema#nonNegativeInteger"), null),
                null,
                Collections.emptySet()
        );
        PointRange range = new PointRange(
                new burp.parse.turtleprov.Point(10, 4),
                new burp.parse.turtleprov.Point(10, 25)
        );

        RmlErrorDto dto = new RmlErrorDto(
                "Lexical value '-5' is not valid.",
                RER.OntologyDatatypeMismatch.getURI(),
                RER.superClassesOf(RER.OntologyDatatypeMismatch),
                "WARNING",
                List.of(range),
                List.of(new burp.reporting.SampleOccurrence(lineage, po)),
                3,
                "BURP Runtime Ontology Validator"
        );

        String json = gson.toJson(dto);
        RmlErrorDto decoded = gson.fromJson(json, RmlErrorDto.class);

        assertThat(decoded).isNotNull();
        assertThat(decoded.message()).isEqualTo(dto.message());
        assertThat(decoded.errorTypeUri()).isEqualTo(dto.errorTypeUri());
        assertThat(decoded.superClassUris()).isEqualTo(dto.superClassUris());
        assertThat(decoded.severity()).isEqualTo("WARNING");
        assertThat(decoded.ranges()).hasSize(1);
        assertThat(decoded.ranges().getFirst()).isEqualTo(range);
        assertThat(decoded.samples()).hasSize(1);
        assertThat(decoded.samples().getFirst().originLineage()).isEqualTo(lineage);
        assertThat(decoded.samples().getFirst().evaluationResult()).isInstanceOf(RdfPredicateObject.class);
        assertThat(decoded.additionalCount()).isEqualTo(3);
        assertThat(decoded.source()).isEqualTo("BURP Runtime Ontology Validator");
    }
}
