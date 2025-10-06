package burp.model;

import java.io.FileReader;
import java.util.*;

public class LogicalView extends LogicalSource {

    protected List<Iteration> iterations = null;
    public LogicalSource logicalSource;
    public List<Field> fields = new ArrayList<>();

    @Override
	public Iterator<Iteration> iterator() {
        try {
            if (iterations == null) {
//                iterations = new ArrayList<>();
//
//                List<Map<String, Object>> l = new ArrayList<>();
//                int i = 0;
//                logicalSource.iterator().forEachRemaining(x-> {
//                    Map<String, Object> m = new HashMap<>();
//                    m.put("#", i++);
//                    m.put("<i>", x);
//                });

                // TODO
            }
            return iterations.iterator();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

class LogicalIteration extends Iteration {

    public final Map<String, String> map = new HashMap<>();

    public LogicalIteration(Set<Object> nulls) {
        super(nulls);
    }

    @Override
    public List<Object> getValuesFor(String reference) {
        List<Object> l = new ArrayList<>();
        if(!map.containsKey(reference))
            throw new RuntimeException("Attribute " + reference + " does not exist.");

        String o = map.get(reference);
        if(!nulls.contains(o))
            l.add(o);

        return l;
    }

    @Override
    public List<String> getStringsFor(String reference) {
        List<String> l = new ArrayList<>();
        if(!map.containsKey(reference))
            throw new RuntimeException("Attribute " + reference + " does not exist.");

        String o = map.get(reference);
        if(!nulls.contains(o))
            l.add(o);

        return l;
    }

}