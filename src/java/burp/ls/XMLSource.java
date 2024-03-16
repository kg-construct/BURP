package burp.ls;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import burp.iteration.XMLIteration;
import burp.model.Iteration;

class XMLSource extends FileBasedLogicalSource {

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				String contents = Files.readString(Paths.get(getDecompressedFile()), encoding);

				DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = builderFactory.newDocumentBuilder();

				Document xmlDocument = builder.parse(IOUtils.toInputStream(contents, encoding));

				XPath xPath = XPathFactory.newInstance().newXPath();
				NodeList nodes = (NodeList) xPath.compile(iterator).evaluate(xmlDocument, XPathConstants.NODESET);

				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					iterations.add(new XMLIteration(node, nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}