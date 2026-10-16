package burp.ls;

import burp.model.Iteration;
import burp.model.IterationId;
import burp.model.OriginLineage;
import com.opencsv.CSVWriter;
import org.jspecify.annotations.Nullable;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CSVIteration extends Iteration {
    public final Map<String, @Nullable String> map = new LinkedHashMap<>();

    public CSVIteration(@Nullable OriginLineage lineage, String[] header, String[] rec, Set<Object> nulls) {
        super(lineage, nulls);
        if (header != null) {
            for (int i = 0; i < header.length; i++) {
                map.put(header[i], rec != null && i < rec.length ? rec[i] : null);
            }
        }
    }

    public CSVIteration(@Nullable IterationId id, String[] header, String[] rec, Set<Object> nulls) {
        this(id != null ? OriginLineage.of(id) : null, header, rec, nulls);
    }

    public CSVIteration(String[] header, String[] rec, Set<Object> nulls) {
        this((OriginLineage) null, header, rec, nulls);
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
