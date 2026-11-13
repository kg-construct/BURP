package burp.reporting;

import burp.model.MappingDocument;
import burp.parse.ValidationMode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public class RmlExecutionReport {
    private final RmlErrorAggregator logAggregator;
    private @Nullable MappingDocument executionPlan;
    private final Statistics statistics;

    public RmlExecutionReport(
            @Nullable List<RmlError> errors,
            @Nullable MappingDocument executionPlan,
            @Nullable Statistics statistics
    ) {
        this(RmlErrorAggregator.DEFAULT_MAX_SAMPLES, errors, executionPlan, statistics);
    }

    public RmlExecutionReport(
            int maxSamplesPerLog,
            @Nullable List<RmlError> errors,
            @Nullable MappingDocument executionPlan,
            @Nullable Statistics statistics
    ) {
        this.logAggregator = new RmlErrorAggregator(maxSamplesPerLog);
        if (errors != null) {
            errors.forEach(this.logAggregator::add);
        }
        this.executionPlan = executionPlan;
        this.statistics = statistics != null ? statistics : new Statistics();
    }

    public RmlExecutionReport() {
        this(null, null, new Statistics());
    }

    /// Records an error, warning, or informational log entry into the bounded aggregator
    /// without throwing, regardless of validation mode.
    public void addLog(RmlError error) {
        logAggregator.add(error);
    }

    /// Records {@code error} into this report and enforces {@code mode}.
    ///
    /// In {@link ValidationMode#STRICT}, any {@link Severity#ERROR} entry immediately throws
    /// {@link BurpException}; non-fatal {@link Severity#WARNING} and {@link Severity#INFORMATION}
    /// entries are recorded without aborting execution. In {@link ValidationMode#LENIENT}, all
    /// severities are recorded without throwing.
    public void emit(RmlError error, ValidationMode mode) {
        addLog(error);
        if (mode == ValidationMode.STRICT && error.severity() == Severity.ERROR) {
            throw new BurpException(error);
        }
    }

    public List<RmlError> getErrors() {
        return logAggregator.getErrors();
    }

    public List<RmlError> getWarnings() {
        return logAggregator.getWarnings();
    }

    public List<AggregatedRmlError> getAggregatedLogs() {
        return logAggregator.getAggregatedLogs();
    }

    public @Nullable MappingDocument getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(@Nullable MappingDocument executionPlan) {
        this.executionPlan = executionPlan;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return "RmlExecutionReport(logs=" + logAggregator.getAggregatedLogs()
                + ", executionPlan=" + (executionPlan != null ? "MappingDocument" : "null")
                + ", statistics=" + statistics + ")";
    }
}
