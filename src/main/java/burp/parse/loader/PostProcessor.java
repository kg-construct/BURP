package burp.parse.loader;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

@FunctionalInterface
public interface PostProcessor<T> {
    void process(Model model, Resource resource, T instance, ReflectiveRdfObjectLoader loader);
}
