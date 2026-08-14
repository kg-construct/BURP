package burp.ls;

import burp.model.Iteration;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmItem;
import java.util.Set;

public class XMLIteration extends Iteration {
    private final XdmItem node;
    private final XPathCompiler xPathCompiler;

    public XMLIteration(XdmItem node, Set<Object> nulls, XPathCompiler xPathCompiler) {
        super(nulls);
        this.node = node;
        this.xPathCompiler = xPathCompiler;
    }

    public XdmItem getNode() {
        return node;
    }

    public XPathCompiler getXPathCompiler() {
        return xPathCompiler;
    }

    @Override
    public String asString() {
        return node.toString();
    }
}
