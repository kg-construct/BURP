package burp.model.lv;

import burp.model.Iteration;
import burp.model.OriginLineage;
import com.opencsv.CSVWriter;
import org.jspecify.annotations.Nullable;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicalIteration extends Iteration {
    public Map<String, @Nullable Object> map;

    public LogicalIteration(@Nullable OriginLineage lineage, Map<String, @Nullable Object> map, Set<Object> nulls) {
        super(lineage, nulls);
        this.map = map != null ? map : new HashMap<>();
    }

    public LogicalIteration(@Nullable OriginLineage lineage, Set<Object> nulls) {
        this(lineage, new HashMap<>(), nulls);
    }

    public LogicalIteration(Set<Object> nulls) {
        this(null, new HashMap<>(), nulls);
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
        return new LogicalIteration(getLineage(), new HashMap<>(map), nulls);
    }

    public LogicalIteration copyWithLineage(@Nullable OriginLineage newLineage) {
        return new LogicalIteration(newLineage, new HashMap<>(map), nulls);
    }

    public void put(String key, @Nullable Object o) {
        if (map.containsKey(key)) {
            throw new RuntimeException("Attribute " + key + " already exists in logical iteration (duplicate names in fields or joins).");
        }
        map.put(key, o);
    }

    public @Nullable Iteration getIteration(String fieldName) {
        Object o = map.get(fieldName);
        return o instanceof Iteration ? (Iteration) o : null;
    }

    public @Nullable String getIterationString(String fieldName) {
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
        for (Map.Entry<String, @Nullable Object> entry : iteration.map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
