package burp.ls;

import burp.model.Iteration;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;

import java.util.ArrayList;
import java.util.List;

public class XMLReference extends Reference {
    public XMLReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof XMLIteration xmlIteration)) {
            throw new IllegalArgumentException("XMLReference " + reference + " can only be used with XMLIteration.");
        }
        try {
            XPathSelector selector = xmlIteration.getXPathCompiler().compile(reference).load();
            selector.setContextItem(xmlIteration.getNode());
            List<Object> l = new ArrayList<>();
            for (XdmItem item : selector) {
                l.add(item.getStringValue());
            }
            return l;
        } catch (Exception e) {
            throw new BurpException(
                new RmlError(
                    "Error executing XPath: " + reference + " on node " + i + ".",
                    origin,
                    RER.ReferenceFormulationExecutionError,
                    e
                )
            );
        }
    }
}
