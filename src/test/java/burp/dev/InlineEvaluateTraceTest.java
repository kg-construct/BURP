package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.eval.*;
import burp.dev.rml.service.hint.InlineHintsEvaluationMode;
import burp.dev.rml.service.selector.CursorSelector;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Test suite dedicated to Trace Evaluation Mode:
/// coherent iteration propagation, forward & reverse joins, unmatched join suppression,
/// chained multi-hop joins, unlimited limit handling, and server-side iteration stepping.
public class InlineEvaluateTraceTest {

    private static final String STEPPING_MAPPING = "src/test/resources/burp/coherent_stepping/mapping.ttl";
    private static final String CHAINED_MAPPING = "src/test/resources/burp/coherent_stepping/chained_mapping.ttl";

    private RmlDevServerImpl createServer() {
        MappingWorkspace workspace = new MappingWorkspace();
        workspace.setInlineHintsEvaluationMode(InlineHintsEvaluationMode.TRACE);
        return new RmlDevServerImpl(workspace);
    }

    @Test
    public void testChildAnchor_ForwardJoinStepping_DemiMooreAndVenusWilliams() throws Exception {
        RmlDevServerImpl server = createServer();

        // Position on TriplesMap1 (line 6)
        Position cursorOnTm1 = new Position(6, 0);

        // 1. Iteration #0 on TriplesMap1 -> Demi Moore (row 0), join has no match -> TriplesMap2 produces no hints
        InlineEvaluateParams params0 = new InlineEvaluateParams(
                STEPPING_MAPPING,
                new RangeDataContext(0, 1),
                null,
                cursorOnTm1
        );
        List<InlineEvaluateItem> items0 = server.inlineEvaluate(params0).get();
        assertThat(items0).isNotNull().isNotEmpty();

        // TM1 evaluates Demi Moore
        assertThat(items0).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("Demi Moore")));
        assertThat(items0).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("student_20")));

        // TM2 produces no hints (unmatched)
        assertThat(items0).noneMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("Tennis") || v.contains("sport_")));

        // EvaluateResult on TM1 at iteration 0 produces trace summary with unmatched TM2
        EvaluateParams evalParams0 = new EvaluateParams(
                new CursorSelector(STEPPING_MAPPING, cursorOnTm1),
                new RangeDataContext(0, 1)
        );
        List<EvaluateResult> evalResults0 = server.evaluate(evalParams0).get();
        assertThat(evalResults0).isNotNull().isNotEmpty();
        List<String> summary0 = evalResults0.getFirst().traceSummary();
        assertThat(summary0).isNotNull().containsExactly(
                "ex:TriplesMap1 Iteration #0",
                "ex:TriplesMap2: No join match"
        );

        // 2. Iteration #1 on TriplesMap1 -> Venus Williams (row 1), join matches -> TriplesMap2 evaluates Tennis (row 0)
        InlineEvaluateParams params1 = new InlineEvaluateParams(
                STEPPING_MAPPING,
                new RangeDataContext(1, 2),
                null,
                cursorOnTm1
        );
        List<InlineEvaluateItem> items1 = server.inlineEvaluate(params1).get();
        assertThat(items1).isNotNull().isNotEmpty();

        // TM1 evaluates Venus Williams
        assertThat(items1).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("Venus Williams")));
        assertThat(items1).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("student_10")));

        // TM2 evaluates Tennis with MatchedJoin reason
        assertThat(items1).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("Tennis")));
        assertThat(items1).anyMatch(i -> i.formattedValues().stream().anyMatch(v -> v.contains("sport_100")));

        InlineEvaluateItem tm2NameItem = items1.stream()
                .filter(i -> i.formattedValues().contains("\"Tennis\""))
                .findFirst()
                .orElseThrow();
        assertThat(tm2NameItem.iterationIndex()).isEqualTo(0);
        assertThat(tm2NameItem.iterationReason()).isInstanceOf(IterationReason.MatchedJoin.class);
        IterationReason.MatchedJoin reason = (IterationReason.MatchedJoin) tm2NameItem.iterationReason();
        assertThat(reason.sourceIterationIndex()).isEqualTo(1);
        assertThat(reason.joinConditionSummary()).isEqualTo("$.Sport == $.ID");

        // EvaluateResult on TM1 at iteration 1 produces trace summary with matched TM2
        EvaluateParams evalParams1 = new EvaluateParams(
                new CursorSelector(STEPPING_MAPPING, cursorOnTm1),
                new RangeDataContext(1, 2)
        );
        List<EvaluateResult> evalResults1 = server.evaluate(evalParams1).get();
        assertThat(evalResults1).isNotNull().isNotEmpty();
        List<String> summary1 = evalResults1.getFirst().traceSummary();
        assertThat(summary1).isNotNull().containsExactly(
                "ex:TriplesMap1 Iteration #1",
                "ex:TriplesMap2 Iteration #0 (Matched via join $.Sport == $.ID from ex:TriplesMap1 Iteration #1)"
        );
    }

    @Test
    public void testParentAnchor_ReverseJoinStepping() throws Exception {
        RmlDevServerImpl server = createServer();

        // Position on TriplesMap2 (line 34)
        Position cursorOnTm2 = new Position(34, 0);

        // 1. Iteration #0 on TriplesMap2 -> Tennis (ID 100). Reverse join matches Venus Williams (Sport 100)
        InlineEvaluateParams params0 = new InlineEvaluateParams(
                STEPPING_MAPPING,
                new RangeDataContext(0, 1),
                null,
                cursorOnTm2
        );
        List<InlineEvaluateItem> items0 = server.inlineEvaluate(params0).get();
        assertThat(items0).isNotNull().isNotEmpty();
        assertThat(items0).anyMatch(i -> i.formattedValues().contains("\"Tennis\""));
        assertThat(items0).anyMatch(i -> i.formattedValues().contains("\"Venus Williams\""));

        EvaluateParams evalParams0 = new EvaluateParams(
                new CursorSelector(STEPPING_MAPPING, cursorOnTm2),
                new RangeDataContext(0, 1)
        );
        List<EvaluateResult> evalResults0 = server.evaluate(evalParams0).get();
        assertThat(evalResults0).isNotNull().isNotEmpty();
        List<String> summary0 = evalResults0.getFirst().traceSummary();
        assertThat(summary0).isNotNull().containsExactly(
                "ex:TriplesMap2 Iteration #0",
                "ex:TriplesMap1 Iteration #1 (Matched via join $.Sport == $.ID from ex:TriplesMap2 Iteration #0)"
        );

        // 2. Iteration #1 on TriplesMap2 -> Climbing (ID 200). Reverse join finds no student -> TM1 produces no hints
        InlineEvaluateParams params1 = new InlineEvaluateParams(
                STEPPING_MAPPING,
                new RangeDataContext(1, 2),
                null,
                cursorOnTm2
        );
        List<InlineEvaluateItem> items1 = server.inlineEvaluate(params1).get();
        assertThat(items1).isNotNull().isNotEmpty();
        assertThat(items1).anyMatch(i -> i.formattedValues().contains("\"Climbing\""));
        assertThat(items1).noneMatch(i -> i.formattedValues().contains("\"Venus Williams\"") || i.formattedValues().contains("\"Demi Moore\""));

        EvaluateParams evalParams1 = new EvaluateParams(
                new CursorSelector(STEPPING_MAPPING, cursorOnTm2),
                new RangeDataContext(1, 2)
        );
        List<EvaluateResult> evalResults1 = server.evaluate(evalParams1).get();
        assertThat(evalResults1).isNotNull().isNotEmpty();
        List<String> summary1 = evalResults1.getFirst().traceSummary();
        assertThat(summary1).isNotNull().containsExactly(
                "ex:TriplesMap2 Iteration #1",
                "ex:TriplesMap1: No join match"
        );
    }

    @Test
    public void testChainedMultiHopJoins_StudentEnrollmentCourse() throws Exception {
        RmlDevServerImpl server = createServer();

        Position cursorOnStudent = new Position(6, 0);

        // 1. Student Iteration #0 is Demi Moore (ID 20). No enrollment matches -> EnrollmentMap and CourseMap unmatched
        InlineEvaluateParams params0 = new InlineEvaluateParams(
                CHAINED_MAPPING,
                new RangeDataContext(0, 1),
                null,
                cursorOnStudent
        );
        List<InlineEvaluateItem> items0 = server.inlineEvaluate(params0).get();
        assertThat(items0).isNotNull().isNotEmpty();
        assertThat(items0).anyMatch(i -> i.formattedValues().contains("\"Demi Moore\""));
        assertThat(items0).noneMatch(i -> i.formattedValues().contains("\"Advanced Tennis\"") || i.formattedValues().contains("\"Acting\""));

        EvaluateParams evalParams0 = new EvaluateParams(
                new CursorSelector(CHAINED_MAPPING, cursorOnStudent),
                new RangeDataContext(0, 1)
        );
        List<EvaluateResult> eval0 = server.evaluate(evalParams0).get();
        assertThat(eval0.getFirst().traceSummary()).containsExactly(
                "ex:StudentMap Iteration #0",
                "ex:EnrollmentMap: No join match",
                "ex:CourseMap: No join match"
        );

        // 2. Student Iteration #1 is Venus Williams (ID 10).
        // Matches Enrollment (StudentID 10, CourseID 501), which transitively matches Course (ID 501, Advanced Tennis)
        InlineEvaluateParams params1 = new InlineEvaluateParams(
                CHAINED_MAPPING,
                new RangeDataContext(1, 2),
                null,
                cursorOnStudent
        );
        List<InlineEvaluateItem> items1 = server.inlineEvaluate(params1).get();
        assertThat(items1).isNotNull().isNotEmpty();
        assertThat(items1).anyMatch(i -> i.formattedValues().contains("\"Venus Williams\""));
        assertThat(items1).anyMatch(i -> i.formattedValues().contains("\"Advanced Tennis\""));

        EvaluateParams evalParams1 = new EvaluateParams(
                new CursorSelector(CHAINED_MAPPING, cursorOnStudent),
                new RangeDataContext(1, 2)
        );
        List<EvaluateResult> eval1 = server.evaluate(evalParams1).get();
        assertThat(eval1.getFirst().traceSummary()).containsExactly(
                "ex:StudentMap Iteration #1",
                "ex:EnrollmentMap Iteration #0 (Matched via join $.ID == $.StudentID from ex:StudentMap Iteration #1)",
                "ex:CourseMap Iteration #0 (Matched via join $.CourseID == $.ID from ex:EnrollmentMap Iteration #0)"
        );
    }

    @Test
    public void testServerSideStepping_AdvancesContextAndUpdatesLspInlayHints() throws Exception {
        RmlDevServerImpl server = createServer();

        Position cursorOnTm1 = new Position(6, 0);

        // Initial step: +1 from 0 to 1
        StepInlineIterationParams stepNext = new StepInlineIterationParams(
                new CursorSelector(STEPPING_MAPPING, cursorOnTm1),
                1
        );
        StepInlineIterationResult nextRes = server.stepInlineIteration(stepNext).get();
        assertThat(nextRes.activeIterationIndex()).isEqualTo(1);
        assertThat(nextRes.traceSummary()).containsExactly(
                "ex:TriplesMap1 Iteration #1",
                "ex:TriplesMap2 Iteration #0 (Matched via join $.Sport == $.ID from ex:TriplesMap1 Iteration #1)"
        );

        // Standard LSP textDocument/inlayHint automatically reflects iteration #1
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(STEPPING_MAPPING),
                new Range(new Position(0, 0), new Position(50, 0))
        );
        List<InlayHint> hints1 = server.getTextDocumentService().inlayHint(hintParams).get();
        List<String> hintLabels1 = hints1.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(hintLabels1).anyMatch(l -> l.contains("Venus Williams"));
        assertThat(hintLabels1).anyMatch(l -> l.contains("Tennis"));

        // Step back: -1 from 1 to 0
        StepInlineIterationParams stepPrev = new StepInlineIterationParams(
                new CursorSelector(STEPPING_MAPPING, cursorOnTm1),
                -1
        );
        StepInlineIterationResult prevRes = server.stepInlineIteration(stepPrev).get();
        assertThat(prevRes.activeIterationIndex()).isEqualTo(0);
        assertThat(prevRes.traceSummary()).containsExactly(
                "ex:TriplesMap1 Iteration #0",
                "ex:TriplesMap2: No join match"
        );

        // Standard LSP textDocument/inlayHint automatically reflects iteration #0
        List<InlayHint> hints0 = server.getTextDocumentService().inlayHint(hintParams).get();
        List<String> hintLabels0 = hints0.stream().map(h -> h.getLabel().getLeft()).toList();
        assertThat(hintLabels0).anyMatch(l -> l.contains("Demi Moore"));
        assertThat(hintLabels0).noneMatch(l -> l.contains("Tennis") || l.contains("sport_"));
    }

    @Test
    public void testUnlimitedJoinIndexLimit_NullMeansUnlimited() throws Exception {
        String uri = "src/test/resources/burp/coherent_join/mapping.ttl";
        RmlDevServerImpl server = createServer();
        // Explicitly null -> unlimited
        server.getWorkspace().setJoinIndexLimit(null);
        assertThat(server.getWorkspace().getJoinIndexLimit()).isNull();
        InlayHintParams hintParams = new InlayHintParams(
                new TextDocumentIdentifier(uri),
                new Range(new Position(0, 0), new Position(50, 0))
        );
        List<InlayHint> hints = server.getTextDocumentService().inlayHint(hintParams).get();
        assertThat(hints).isNotNull().isNotEmpty();

        // Unlimited scan matches parent row #2 (Research)
        InlayHint parentSubjectHint = hints.stream()
                .filter(h -> h.getPosition().getLine() >= 26
                        && h.getTooltip().getRight().getValue().contains("**SubjectMap Evaluation**"))
                .findFirst()
                .orElseThrow();
        assertThat(parentSubjectHint.getLabel().getLeft()).contains("ex:department/Research");
    }
}
