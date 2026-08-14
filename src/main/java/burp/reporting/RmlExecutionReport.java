package burp.reporting;

import burp.model.MappingDocument;
import java.util.ArrayList;
import java.util.List;

public class RmlExecutionReport {
    private final List<RmlError> errors;
    private MappingDocument executionPlan;
    private final Statistics statistics;

    public RmlExecutionReport(List<RmlError> errors, MappingDocument executionPlan, Statistics statistics) {
        this.errors = errors != null ? errors : new ArrayList<>();
        this.executionPlan = executionPlan;
        this.statistics = statistics != null ? statistics : new Statistics();
    }

    public RmlExecutionReport() {
        this(new ArrayList<>(), null, new Statistics());
    }

    public List<RmlError> getErrors() {
        return errors;
    }

    public MappingDocument getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(MappingDocument executionPlan) {
        this.executionPlan = executionPlan;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return "RmlExecutionReport(errors=" + errors + ", executionPlan=" + (executionPlan != null ? "MappingDocument" : "null") + ", statistics=" + statistics + ")";
    }
}
