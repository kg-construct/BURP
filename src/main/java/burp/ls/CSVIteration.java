package burp.ls;

import burp.model.Iteration;
import com.opencsv.CSVWriter;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CSVIteration extends Iteration {
    public final Map<String, String> map = new LinkedHashMap<>();

    public CSVIteration(String[] header, String[] rec, Set<Object> nulls) {
        super(nulls);
        if (header != null) {
            for (int i = 0; i < header.length; i++) {
                map.put(header[i], rec[i]);
            }
        }
    }

    @Override
    public String asString() {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter writer = new CSVWriter(stringWriter)) {
            String[] header = map.keySet().toArray(new String[0]);
            writer.writeNext(header);
            String[] rec = map.values().toArray(new String[0]);
            writer.writeNext(rec);
        } catch (Exception e) {
            throw new RuntimeException("Error representing CSV iteration as CSV.", e);
        }
        return stringWriter.toString();
    }
}
