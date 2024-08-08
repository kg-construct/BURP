// Follow RDBSource for inspiration
// Here we import the NETCONF Library and instantiate the client

package burp.ls;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.tailf.jnc.Element;
import com.tailf.jnc.JNCException;
import com.tailf.jnc.NetconfSession;
import com.tailf.jnc.Prefix;
import com.tailf.jnc.PrefixMap;
import com.tailf.jnc.SSHConnection;
import com.tailf.jnc.SSHSession;
import com.tailf.jnc.XMLParser;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.util.SimpleNamespaceContext;
import burp.vocabularies.YANG;

class YANGSource extends LogicalSource {

	public String endpoint;
	public String password;
	public String username;
	public String iterator;
	public Resource datastore;
	public HashMap<String, String> prefixMap;

	public String subtreeValue = null;

	protected List<Iteration> iterations = null;
	public Charset encoding = StandardCharsets.UTF_8;

	public Map<Resource, Integer> datastoreMap = Map.ofEntries(
    	Map.entry(YANG.RunningDatastore, NetconfSession.RUNNING),
	  	Map.entry(YANG.CandidateDatastore, NetconfSession.CANDIDATE),
	  	Map.entry(YANG.StartupDatastore, NetconfSession.STARTUP)
  	);

	@Override
	public Iterator<Iteration> iterator() {
		//Element.setDebugLevel(2); # Helpful for debugging
		SSHConnection c = new SSHConnection();
		String contents = null;
		try {
			String host = endpoint.split(":")[0];
			int port = Integer.parseInt(endpoint.split(":")[1]);
			c.setHostVerification(null).connect(host, port);
			c.authenticateWithPassword(username, password);
			SSHSession ssh = new SSHSession(c);
			NetconfSession nc = new NetconfSession(ssh);
			// Set prefix map for NETCONF operations
			PrefixMap pm = new PrefixMap();
			for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
				pm.add(new Prefix(
					entry.getKey(),
					entry.getValue())
				);
			}
			// Select NETCONF operation based on type of datastore
			if (datastore.hasProperty(RDF.type, YANG.OperationalDatastore)) {
				if (subtreeValue != null) {
					// Subtree filter
					Element subtree = new XMLParser().parse(subtreeValue);
					contents= nc.get(subtree).toXMLString();
				} else {
					// XPath filter
					String xpath = iterator;
					contents = nc.get(xpath, pm).toXMLString();
				}
			} else {
				// Conventional (configuration) datastores
				if (subtreeValue != null) {
					// Subtree filter
					Element subtree = new XMLParser().parse(subtreeValue);
					contents= nc.getConfig(
						datastoreMap.get(datastore.getPropertyResourceValue(RDF.type)),
						subtree).toXMLString();
				} else {
					// XPath filter
					String xpath = iterator;
					contents = nc.getConfig(
						datastoreMap.get(datastore.getPropertyResourceValue(RDF.type)),
							xpath, pm).toXMLString();
				}
			}
			c.close();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		} catch (JNCException ex) {
			throw new RuntimeException(ex);
		}

		// XML data fetched from YANG server. Now iterate.
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
				builderFactory.setNamespaceAware(true);
				DocumentBuilder builder = builderFactory.newDocumentBuilder();
				Document xmlDocument = builder.parse(IOUtils.toInputStream(contents, encoding));

				XPath xPath = XPathFactory.newInstance().newXPath();
				SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefixMap);
				xPath.setNamespaceContext(namespaces);

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
