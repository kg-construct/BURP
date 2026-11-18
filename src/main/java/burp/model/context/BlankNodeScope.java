package burp.model.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Request-scoped, thread-safe blank node identifier generator and cache.
 */
public final class BlankNodeScope {
    private final AtomicLong counter = new AtomicLong(0);
    private final Map<Object, String> idMap = new ConcurrentHashMap<>();

    public String getOrAllocate(Object key) {
        if (key == null) {
            return allocateFresh();
        }
        return idMap.computeIfAbsent(key, k -> "bnode-" + counter.incrementAndGet());
    }

    public String allocateFresh() {
        return "bnode-" + counter.incrementAndGet();
    }

    public String allocateFresh(String prefix) {
        return prefix + counter.incrementAndGet();
    }
}
