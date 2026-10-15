package burp.model;

import burp.dev.rml.service.plan.PlanNodeDescriptor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe registry for dynamically assigning and retrieving unique, short IDs for PlanNode instances.
 */
@NullMarked
public final class PlanNodeIdRegistry {
    private static final Map<PlanNode, String> ids = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, PlanNode> nodesById = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> countsByAbbr = new ConcurrentHashMap<>();

    private PlanNodeIdRegistry() {}

    public static String getId(PlanNode node) {
        return ids.computeIfAbsent(node, n -> {
            String abbr = PlanNodeDescriptor.getAbbreviation(n);
            int count = countsByAbbr.computeIfAbsent(abbr, k -> new AtomicInteger(0)).incrementAndGet();
            String id = abbr + count;
            nodesById.put(id, n);
            return id;
        });
    }

    public static void setId(PlanNode node, String id) {
        ids.put(node, id);
        nodesById.put(id, node);
    }

    public static @Nullable PlanNode getNodeById(String id) {
        return nodesById.get(id);
    }

    public static void clear() {
        ids.clear();
        nodesById.clear();
        countsByAbbr.clear();
    }
}
