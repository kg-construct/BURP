package burp.model;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LogicalView extends AbstractLogicalSource {

    private List<LogicalIteration> iterations = null;

    public LogicalSource logicalSource;
    public List<ExpressionField> expressionFields = new ArrayList<>();
    public List<IterableField> iterableFields = new ArrayList<>();

    @Override
	public Iterator<Iteration> iterator() {
        try {
            if (iterations == null) {
                iterations = new ArrayList<>();

                // The logical view acts like the root
                IterableField root = new IterableField();
                root.referenceFormulation = logicalSource.referenceFormulation;
                root.iterator = logicalSource.iterator;
                root.expressionFields.addAll(expressionFields);
                root.iterableFields.addAll(iterableFields);
                root.fieldName = "<i>";
                iterations = root.enrich(logicalSource.iterator(), logicalSource.referenceFormulation, logicalSource.iterator, logicalSource.nulls);

                iterations.iterator();
            }

            // Create wrapper to safely treat LogicalIterations as Iterations...
            return new Iterator<>() {
                private final Iterator<LogicalIteration> inner = iterations.iterator();

                @Override
                public boolean hasNext() {
                    return inner.hasNext();
                }

                @Override
                public Iteration next() {
                    return inner.next();
                }
            };

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void addField(Field field) {
        System.out.println(field);
        if (field instanceof IterableField) {
            iterableFields.add((IterableField) field);
        } else if (field instanceof ExpressionField) {
            expressionFields.add((ExpressionField) field);
        }
        else
            throw new RuntimeException("Unknown field type.");
    }
}

class LogicalIteration extends Iteration {

    public Map<String, Object> map = new HashMap<>();

    public LogicalIteration(Set<Object> nulls) {
        super(nulls);
    }

    public LogicalIteration(Map<String, Object> map, Set<Object> nulls) {
        super(nulls);
        this.map = map;
    }

    @Override
    public List<Object> getValuesFor(String reference) {
        List<Object> l = new ArrayList<>();
        if(!map.containsKey(reference))
            throw new RuntimeException("Attribute " + reference + " does not exist.");

        Object o = map.get(reference);
        if(nulls == null || !nulls.contains(o))
            l.add(o);

        return l;
    }

    @Override
    public List<String> getStringsFor(String reference) {
        return getValuesFor(reference).stream().map(Object::toString).collect(Collectors.toList());
    }

    public String toString() {
        Map<String, Integer> widths = new LinkedHashMap<>();
        for (var e : map.entrySet()) {
            int width = Math.max(e.getKey().length(), String.valueOf(e.getValue()).length());
            widths.put(e.getKey(), width);
        }

        StringBuilder sb = new StringBuilder();

        // Build horizontal line
        String line = "+" + widths.values().stream()
                .map(w -> "-".repeat(w + 2))
                .reduce("", (a, b) -> a + "+" + b)
                .substring(1) + "+";

        // Header row (keys)
        sb.append(line).append("\n");
        sb.append("|");
        for (var e : map.entrySet()) {
            sb.append(" ").append(String.format("%-" + widths.get(e.getKey()) + "s", e.getKey())).append(" |");
        }
        sb.append("\n").append(line).append("\n");

        // Value row
        sb.append("|");
        for (var e : map.entrySet()) {
            sb.append(" ").append(String.format("%-" + widths.get(e.getKey()) + "s", e.getValue())).append(" |");
        }
        sb.append("\n").append(line);

        return sb.toString();
    }

    public LogicalIteration copy() {
        return new LogicalIteration(map,null);
    }

    public void put(String key, Object o) {
        map.put(key, o);
    }

    public Iteration getIteration(String fieldName) {
        return (Iteration) map.get(fieldName);
    }
}