package burp;

public class TestData {

    public String ID;
    public String title;
    public String description;
    public String specification;
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

    TestData(String ID,
             String title,
             String description,
             String specification,
             String mapping,
             String input_format1,
             String input_format2,
             String input_format3,
             String output_format1,
             String output_format2,
             String output_format3,
             String input1,
             String input2,
             String input3,
             String output1,
             String output2,
             String output3,
             String error) {
        this.ID = ID;
        this.title = title;
        this.description = description;
        this.specification = specification;
        this.mapping = mapping;
        this.input_format1 = input_format1;
        this.input_format2 = input_format2;
        this.input_format3 = input_format3;
        this.output_format1 = output_format1;
        this.output_format2 = output_format2;
        this.output_format3 = output_format3;
        this.input1 = input1;
        this.input2 = input2;
        this.input3 = input3;
        this.output1 = output1;
        this.output2 = output2;
        this.output3 = output3;
        this.error = "true".equalsIgnoreCase(error);
    }

    public String toString() {
        return ID;
    }
}
