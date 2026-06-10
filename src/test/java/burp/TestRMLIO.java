package burp;

public class TestRMLIO extends TestRMLModule {
    @Override
    public String getBase() {
        return "./src/test/resources/rml-io/";
    }

    private boolean isIgnored(String id) {
        return id.startsWith("RMLSTC0011") ||
               id.equals("RMLSTC0006a") ||
               id.equals("RMLSTC0009a") ||
               id.equals("RMLTTC0004a") ||
               id.equals("RMLTTC0004c") ||
               id.equals("RMLTTC0004e") ||
               id.equals("RMLTTC0004f") ||
               id.equals("RMLTTC0004g") ||
               id.equals("RMLTTC0005b") ||
               id.equals("RMLTTC0006b") ||
               id.equals("RMLTTC0006c") ||
               id.equals("RMLTTC0006d") ||
               id.equals("RMLTTC0006e");
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
