package burp.model;

import java.util.Set;

public abstract class Iteration {
    public final Set<Object> nulls;

    public Set<Object> getNulls() { return nulls; }

    public Iteration(Set<Object> nulls) {
        this.nulls = nulls;
    }

    public abstract String asString();
}