package burp.ls;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import burp.model.Iteration;
import burp.util.SimpleNamespaceContext;

class XMLSource extends FileBasedLogicalSource {

	public HashMap<String, String> prefixMap;

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				String contents = Files.readString(Paths.get(getDecompressedFile()), encoding);

				DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
				if (prefixMap != null) {
					// Required for prefix evaluation of XPath expression
					builderFactory.setNamespaceAware(true);
				}
				DocumentBuilder builder = builderFactory.newDocumentBuilder();
				Document xmlDocument = builder.parse(IOUtils.toInputStream(contents, encoding));

				XPath xPath = XPathFactory.newInstance().newXPath();
				if (prefixMap != null) {
					SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefixMap);
					xPath.setNamespaceContext(namespaces);
				}

				NodeList nodes = (NodeList) xPath.compile(iterator).evaluate(xmlDocument, XPathConstants.NODESET);

				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					iterations.add(new XMLIteration(node, nulls, prefixMap));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class XMLIteration extends Iteration {

	private Node node;
	private HashMap<String, String> prefixMap;

	protected XMLIteration(Node node, Set<Object> nulls, HashMap<String, String> prefixMap) {
		super(nulls);

		this.node = node;
		this.prefixMap = prefixMap;
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		// We need to explicitly convert the objects
		// to strings because RML has not worked out
		// "6.6.1 Automatically deriving datatypes" yet
		List<Object> l2 = new ArrayList<Object>();
		try {
			XPath xPath = XPathFactory.newInstance().newXPath();
			if (prefixMap != null) {
				SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefixMap);
				xPath.setNamespaceContext(namespaces);
			}
			NodeList nodes = (NodeList) xPath.compile(reference).evaluate(node, XPathConstants.NODESET);
			for(int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(0);
				if(node.getTextContent() != null && !nulls.contains(node.getTextContent()))
					l2.add(node.getTextContent());
			}

		} catch (Exception e) {
			// No data, silently ignore
			e.printStackTrace();
		}
		return l2;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l2 = new ArrayList<String>();
		try {
			XPath xPath = XPathFactory.newInstance().newXPath();
			if (prefixMap != null) {
				SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefixMap);
				xPath.setNamespaceContext(namespaces);
			}
			NodeList nodes = (NodeList) xPath.compile(reference).evaluate(node, XPathConstants.NODESET);
			for(int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(0);
				if(node.getTextContent() != null && !nulls.contains(node.getTextContent()))
					l2.add(node.getTextContent());
			}

		} catch (Exception e) {
			// No data, silently ignore
			e.printStackTrace();
		}
		return l2;
	}

}
