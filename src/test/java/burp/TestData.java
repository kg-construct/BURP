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

    TestData(Map<String, String> data) {
        this.ID = data.get("ID");
        this.title = data.get("title");
        this.description = data.get("description");
        this.specification = data.get("specification");
        this.baseIRI = data.getOrDefault("baseIRI", null);
        this.mapping = data.get("mapping");
        this.input_format1 = data.getOrDefault("input_format1", null);
        this.input_format2 = data.getOrDefault("input_format2", null);
        this.input_format3 = data.getOrDefault("input_format3", null);
        this.output_format1 = data.getOrDefault("output_format1", null);
        this.output_format2 = data.getOrDefault("output_format2", null);
        this.output_format3 = data.getOrDefault("output_format3", null);
        this.input1 = data.getOrDefault("input1", null);
        this.input2 = data.getOrDefault("input2", null);
        this.input3 = data.getOrDefault("input3", null);
        this.output1 = data.getOrDefault("output1", null);
        this.output2 = data.getOrDefault("output2", null);
        this.output3 = data.getOrDefault("output3", null);
        String error = data.get("error");
        this.error = "true".equalsIgnoreCase(error);
    }

    public String toString() {
        return ID;
    }
}
