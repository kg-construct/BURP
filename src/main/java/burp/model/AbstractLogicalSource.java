package burp.model;

import org.apache.jena.rdf.model.Resource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class AbstractLogicalSource extends Iterable {

    public Set<Object> nulls = new HashSet<>();
    public abstract Iterator<Iteration> iterator();

}