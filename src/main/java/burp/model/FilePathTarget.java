package burp.model;

import org.apache.jena.rdf.model.Resource;

public class FilePathTarget extends RMLTarget {
    private String path;
    private Resource root;

    public FilePathTarget(String path, Resource root) {
        this.path = path;
        this.root = root;
    }

    public String getPath() {
        return path;
    }

    public Resource getRoot() {
        return root;
    }
}
