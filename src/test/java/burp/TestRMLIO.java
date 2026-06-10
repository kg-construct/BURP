package burp;

import java.util.HashSet;
import java.util.List;

public class TestRMLIO extends TestRMLModule {
    @Override
    public String getBase() {
        return "./src/test/resources/rml-io/";
    }

    private boolean isIgnored(String id) {
        var buggyTests = List.of(
                // https://github.com/kg-construct/rml-io/issues/167
                "RMLSTC0011a",
                "RMLSTC0011b",
                "RMLSTC0011c",
                "RMLSTC0011d",
                "RMLSTC0011e",
                // https://github.com/kg-construct/rml-io/issues/147
                "RMLSTC0006a",
                // https://github.com/kg-construct/rml-io/issues/150
                "RMLSTC0009a",
                // https://github.com/kg-construct/rml-io/issues/151
                "RMLTTC0004a",
                "RMLTTC0004c",
                "RMLTTC0004e",
                "RMLTTC0004f",
                "RMLTTC0004g",
                "RMLTTC0005b",
                "RMLTTC0006b",
                "RMLTTC0006c",
                "RMLTTC0006d",
                "RMLTTC0006e"
        );
        return (new HashSet<>(buggyTests)).contains(id);
    }

    @Override
    protected java.util.stream.Stream<TestData> testDataProviderOK() throws java.io.IOException, com.opencsv.exceptions.CsvException {
        return super.testDataProviderOK().filter(t -> !isIgnored(t.ID));
    }

    @Override
    protected java.util.stream.Stream<TestData> testDataProviderNotOK() throws java.io.IOException, com.opencsv.exceptions.CsvException {
        return super.testDataProviderNotOK().filter(t -> !isIgnored(t.ID));
    }
}
