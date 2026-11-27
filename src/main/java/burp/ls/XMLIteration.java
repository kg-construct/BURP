package burp.ls;

import burp.model.Iteration;
import burp.model.IterationId;
import burp.model.OriginLineage;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmItem;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public class XMLIteration extends Iteration {
    private final XdmItem node;
    private final XPathCompiler xPathCompiler;

    public XMLIteration(@Nullable OriginLineage lineage, XdmItem node, Set<Object> nulls, XPathCompiler xPathCompiler) {
        super(lineage, nulls);
        this.node = node;
        this.xPathCompiler = xPathCompiler;
    }

    public XMLIteration(@Nullable IterationId id, XdmItem node, Set<Object> nulls, XPathCompiler xPathCompiler) {
        this(id != null ? OriginLineage.of(id) : null, node, nulls, xPathCompiler);
    }

    public XMLIteration(XdmItem node, Set<Object> nulls, XPathCompiler xPathCompiler) {
        this((OriginLineage) null, node, nulls, xPathCompiler);
    }

    public XdmItem getNode() {
        return node;
    }

    public XPathCompiler getXPathCompiler() {
        return xPathCompiler;
    }

    @Override
    public String asString() {
        return node != null ? node.toString() : "";
    }
}
