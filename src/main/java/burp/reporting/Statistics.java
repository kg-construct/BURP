package burp.reporting;

import burp.model.TriplesMap;
import java.util.Collections;
import java.util.Map;

public class Statistics {
    private Map<TriplesMap, Long> generatedStatementPerTriplesMap;
    private long generatedStatements;

    public Statistics(Map<TriplesMap, Long> generatedStatementPerTriplesMap, long generatedStatements) {
        this.generatedStatementPerTriplesMap = generatedStatementPerTriplesMap != null ? generatedStatementPerTriplesMap : Collections.emptyMap();
        this.generatedStatements = generatedStatements;
    }

    public Statistics() {
        this(Collections.emptyMap(), 0);
    }

    public Map<TriplesMap, Long> getGeneratedStatementPerTriplesMap() {
        return generatedStatementPerTriplesMap;
    }

    public void setGeneratedStatementPerTriplesMap(Map<TriplesMap, Long> generatedStatementPerTriplesMap) {
        this.generatedStatementPerTriplesMap = generatedStatementPerTriplesMap;
    }

    public long getGeneratedStatements() {
        return generatedStatements;
    }

    public void setGeneratedStatements(long generatedStatements) {
        this.generatedStatements = generatedStatements;
    }

    @Override
    public String toString() {
        return "Statistics(generatedStatementPerTriplesMap=" + generatedStatementPerTriplesMap + ", generatedStatements=" + generatedStatements + ")";
    }
}
