package burp;

import java.util.Map;

public class TestData {

    public String ID;
    public String title;
    public String description;
    public String specification;
    public String baseIRI;
    public String mapping;
    public String input_format1;
    public String input_format2;
    public String input_format3;
    public String output_format1;
    public String output_format2;
    public String output_format3;
    public String input1;
    public String input2;
    public String input3;
    public String output1;
    public String output2;
    public String output3;
    public boolean error;

    public TestData(Map<String, String> data) {
        this.ID = data.get("ID");
        this.title = data.get("title");
        this.description = data.get("description");
        this.specification = data.get("specification");
        this.baseIRI = data.getOrDefault("base_iri", "http://example.com/base/");
        this.mapping = data.get("mapping");
        this.input_format1 = data.get("input_format1");
        this.input_format2 = data.get("input_format2");
        this.input_format3 = data.get("input_format3");
        this.output_format1 = data.get("output_format1");
        this.output_format2 = data.get("output_format2");
        this.output_format3 = data.get("output_format3");
        this.input1 = data.get("input1");
        this.input2 = data.get("input2");
        this.input3 = data.get("input3");
        this.output1 = data.get("output1");
        this.output2 = data.get("output2");
        this.output3 = data.get("output3");
        this.error = "true".equalsIgnoreCase(data.get("error"));
    }

    public String toString() {
        return ID;
    }
}
