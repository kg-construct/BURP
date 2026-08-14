package burp;

import java.util.List;

public class TestRMLIO extends TestRMLModule {
    @Override
    public String getBase() {
        return "./target/test-classes/rml-io/";
    }

    public List<String> buggyTests() {
        return List.of(
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
    }
}
