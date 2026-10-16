package burp.dev;

import burp.dev.rml.RmlDevServerImpl;
import burp.dev.rml.service.selector.CursorSelector;
import burp.dev.rml.service.plan.PlanParams;
import burp.dev.rml.service.plan.PlanResult;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CursorSelectionTest {

    private static final String MAPPING_GATHER_URI = "src/test/resources/burp/cursor_selection/mapping_gather.ttl";
    private static final String MAPPING_FUNCTION_URI = "src/test/resources/burp/cursor_selection/mapping_function.ttl";

    @Test
    public void cursorOnPredicateObjectMap_shouldResolveToPredicateObjectMap() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // rml:predic|ateObjectMap [
        PlanResult res = server.plan(new PlanParams(MAPPING_GATHER_URI, new CursorSelector(MAPPING_GATHER_URI, new Position(21, 14)))).get();
        assertThat(res.selectedNodeId()).isEqualTo("pom1");
    }

    @Test
    public void cursorOnGraphMap_shouldResolveToGraphMap() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // rml:gr|aphMap [
        PlanResult res = server.plan(new PlanParams(MAPPING_GATHER_URI, new CursorSelector(MAPPING_GATHER_URI, new Position(22, 14)))).get();
        assertThat(res.selectedNodeId()).isEqualTo("gm1");
    }

    @Test
    public void cursorOnObjectMap_shouldResolveToObjectMap() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // rml:objectMap [|
        PlanResult res = server.plan(new PlanParams(MAPPING_GATHER_URI, new CursorSelector(MAPPING_GATHER_URI, new Position(24, 23)))).get();
        assertThat(res.selectedNodeId()).isEqualTo("om1");
    }

    @Test
    public void cursorOnGatherKeyword_shouldResolveToGatherMapOrObjectMap() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // rml:g|ather (
        PlanResult res = server.plan(new PlanParams(MAPPING_GATHER_URI, new CursorSelector(MAPPING_GATHER_URI, new Position(25, 16)))).get();
        assertThat(res.selectedNodeId()).isIn("gam1", "om1");
    }

    @Test
    public void cursorOnGatherAsKeyword_shouldResolveToGatherMapOrObjectMap() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // rml:gatherAs |rdf:List
        PlanResult res = server.plan(new PlanParams(MAPPING_GATHER_URI, new CursorSelector(MAPPING_GATHER_URI, new Position(26, 25)))).get();
        assertThat(res.selectedNodeId()).isIn("gam1", "om1");
    }

    @Test
    public void cursorOnInnerReferenceInsideGather_shouldResolveToInnerObjectMapOrReference() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // [ |rml:reference
        PlanResult res = server.plan(new PlanParams(MAPPING_GATHER_URI, new CursorSelector(MAPPING_GATHER_URI, new Position(25, 27)))).get();
        assertThat(res.selectedNodeId()).isIn("om2", "rawreference2");
    }

    @Test
    public void cursorOnFunctionObjectMap_shouldResolveToObjectMap() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // line 25 (0-indexed 24, col 23): on "rml:objectMap ["
        PlanResult res = server.plan(new PlanParams(MAPPING_FUNCTION_URI, new CursorSelector(MAPPING_FUNCTION_URI, new Position(24, 23)))).get();
        assertThat(res.selectedNodeId()).isEqualTo("om1");
    }

    @Test
    public void cursorOnFunctionExecution_shouldResolveToFunctionExecution() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // line 26 (0-indexed 25, col 25): on "rml:functionExecution ["
        PlanResult res = server.plan(new PlanParams(MAPPING_FUNCTION_URI, new CursorSelector(MAPPING_FUNCTION_URI, new Position(25, 25)))).get();
        assertThat(res.selectedNodeId()).isIn("fe1", "functionexecution1");
    }

    @Test
    public void cursorOnFunctionKeyword_shouldResolveToFunctionMapOrExecution() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // line 27 (0-indexed 26, col 25): on "rml:function idlab-fn:concat"
        PlanResult res = server.plan(new PlanParams(MAPPING_FUNCTION_URI, new CursorSelector(MAPPING_FUNCTION_URI, new Position(26, 25)))).get();
        assertThat(res.selectedNodeId()).isIn("fe1", "functionexecution1", "functionmap1", "rdfnodeconstant1", "rdfnodeconstant2");
    }

    @Test
    public void cursorOnFunctionInput_shouldResolveToInput() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // line 28 (0-indexed 27, col 25): on "rml:input ["
        PlanResult res = server.plan(new PlanParams(MAPPING_FUNCTION_URI, new CursorSelector(MAPPING_FUNCTION_URI, new Position(27, 25)))).get();
        assertThat(res.selectedNodeId()).isIn("input1", "input2", "in1", "in2");
    }

    @Test
    public void cursorOnFunctionInputValueMap_shouldResolveToInputValueMapOrReference() throws Exception {
        RmlDevServerImpl server = new RmlDevServerImpl();
        // line 30 (0-indexed 29, col 45): on "rml:reference \"$.firstName\""
        PlanResult res = server.plan(new PlanParams(MAPPING_FUNCTION_URI, new CursorSelector(MAPPING_FUNCTION_URI, new Position(29, 45)))).get();
        assertThat(res.selectedNodeId()).isIn("inputvaluemap1", "inputvaluemap2", "rawreference1", "rawreference2", "rawreference3", "ref1", "ref2");
    }
}
