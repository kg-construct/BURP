package burp.model.lv;

import burp.model.Iteration;
import com.opencsv.CSVWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicalIteration extends Iteration {
    public Map<String, Object> map;

    public LogicalIteration(Set<Object> nulls) {
        super(nulls);
        this.map = new HashMap<>();
    }

    public LogicalIteration(Map<String, Object> map, Set<Object> nulls) {
        super(nulls);
        this.map = map;
    }

    @Override
    public String asString() {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter writer = new CSVWriter(stringWriter)) {
            String[] header = map.keySet().toArray(new String[0]);
            writer.writeNext(header);
            String[] rec = map.values().stream()
                    .map(o -> o == null ? "null" : o.toString())
                    .toArray(String[]::new);
            writer.writeNext(rec);
        } catch (Exception e) {
            throw new RuntimeException("Error representing logical iteration as String/CSV.", e);
        }
        return stringWriter.toString();
    }

    public LogicalIteration copy() {
        return new LogicalIteration(new HashMap<>(map), nulls);
    }

    public void put(String key, Object o) {
        if (map.containsKey(key)) {
            throw new RuntimeException("Attribute " + key + " already exists in logical iteration (duplicate names in fields or joins).");
        }
        map.put(key, o);
    }

    public Iteration getIteration(String fieldName) {
        Object o = map.get(fieldName);
        return o instanceof Iteration ? (Iteration) o : null;
    }

    public String getIterationString(String fieldName) {
        Object o = map.get(fieldName);
        if (o instanceof Iteration) {
            return ((Iteration) o).asString();
        }
        return o == null ? null : o.toString();
    }

    public void retainKeys(List<String> keys) {
        map.keySet().retainAll(keys);
    }

    public void add(LogicalIteration iteration) {
        for (Map.Entry<String, Object> entry : iteration.map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
