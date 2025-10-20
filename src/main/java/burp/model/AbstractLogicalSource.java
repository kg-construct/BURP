package burp.model;

import org.apache.jena.rdf.model.Resource;

import java.util.*;

public abstract class AbstractLogicalSource extends Iterable implements FieldParent {

    public Set<Object> nulls = new HashSet<>();
    public abstract Iterator<Iteration> iterator();

    @Override
    public String getAbsoluteFieldName() {
        return "<i>";
    }
}