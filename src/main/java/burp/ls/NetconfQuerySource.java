// Follow RDBSource for inspiration
// Here we import the NETCONF Library and instantiate the client

package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.model.Reference;
import burp.reporting.Origin;
import burp.util.SimpleNamespaceContext;
import burp.vocabularies.YS;
import com.github.candildatafabric.jnc.*;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class NetconfQuerySource extends LogicalSource {

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return null;
    }

	public String endpoint;
	public String password;
	public String username;
	public Resource datastoreType;
	public Resource filter;

	public HashMap<String, String> rmlPrefixMap;
    public String iterator;
    public Resource referenceFormulation;
    public Resource getReferenceFormulation() { return referenceFormulation; }

    protected List<Iteration> iterations = null;
	public Charset encoding = StandardCharsets.UTF_8;

	private final Map<Resource, Integer> datastoreMap = Map.ofEntries(
	  	Map.entry(YS.CandidateDatastore, NetconfSession.CANDIDATE),
	  	Map.entry(YS.StartupDatastore, NetconfSession.STARTUP),
		Map.entry(YS.RunningDatastore, NetconfSession.RUNNING)
  	);
	private PrefixMap netconfPrefixMap;

	@Override
	public Iterable<Iteration> iterator() {
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
			// Set prefix map for NETCONF filtering based on XPath
			if (filter.hasProperty(RDF.type, YS.XPathFilter)) {
				// Set map of namespaces for XPath iteration
				StmtIterator properties = filter.listProperties(YS.namespace);
				HashMap<String, String> prefixMap = new HashMap<>();
				while (properties.hasNext()) {
					Statement statement = properties.next();
					Resource namespace = statement.getResource();
					prefixMap.put(
						namespace.getProperty(YS.namespacePrefix).getLiteral().getString(),
						namespace.getProperty(YS.namespaceURL).getLiteral().getString()
					);
				}
				netconfPrefixMap = new PrefixMap();
				for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
					netconfPrefixMap.add(new Prefix(
						entry.getKey(),
						entry.getValue())
					);
				}
			}
			// Select NETCONF operation based on type of datastore
			//
			// NETCONF Operational datastore
			if (datastoreType.equals(YS.OperationalDatastore)) {
				// XPath filter
				if (filter.hasProperty(RDF.type, YS.XPathFilter)) {
					String xpath = filter.getProperty(YS.xpathValue).getLiteral().getString();
					contents = nc.get(xpath, netconfPrefixMap).toXMLString();
				} else { // Subtree filter
					Element subtree = new XMLParser().parse(
						filter.getProperty(YS.subtreeValue).getLiteral().getString());
					contents= nc.get(subtree).toXMLString();
				}
			} else { // Conventional (configuration) datastores
				// XPath filter
				if (filter.hasProperty(RDF.type, YS.XPathFilter)) {
					String xpath = filter.getProperty(YS.xpathValue).getLiteral().getString();
					contents = nc.getConfig(
						datastoreMap.get(datastoreType), xpath, netconfPrefixMap).toXMLString();
				} else { // Subtree filter
					Element subtree = new XMLParser().parse(
						filter.getProperty(YS.subtreeValue).getLiteral().getString());
					contents= nc.getConfig(
						datastoreMap.get(datastoreType), subtree).toXMLString();
				}
			}
			c.close();
		} catch (IOException | JNCException ex) {
			throw new RuntimeException(ex);
		}

        // XML data fetched from YANG server. Now iterate.
		try {
			if (iterations == null) {
				iterations = new ArrayList<>();

				DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
				if (rmlPrefixMap != null) {
					// Required for prefix evaluation of XPath expression
					builderFactory.setNamespaceAware(true);
				}
				DocumentBuilder builder = builderFactory.newDocumentBuilder();
				Document xmlDocument = builder.parse(IOUtils.toInputStream(contents, encoding));

				XPath xPath = XPathFactory.newInstance().newXPath();
				if (rmlPrefixMap != null) {
					SimpleNamespaceContext namespaces = new SimpleNamespaceContext(rmlPrefixMap);
					xPath.setNamespaceContext(namespaces);
				}

				NodeList nodes = (NodeList) xPath.compile(iterator).evaluate(xmlDocument, XPathConstants.NODESET);

				for (int i = 0; i < nodes.getLength(); i++) {
					Node node = nodes.item(i);
					throw new RuntimeException("XMLIteration with DOM Node is not supported here.");
				}
			}
			return iterations;
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}
