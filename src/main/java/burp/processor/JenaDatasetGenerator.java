package burp.processor;

import burp.model.RdfStatement;
import burp.model.rdf.*;
import burp.vocabularies.RML;
import org.apache.jena.datatypes.BaseDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts BURP RdfStatement sequences into Apache Jena Datasets with multiple graph support.
 */
public class JenaDatasetGenerator {

    public static Dataset toDataset(Iterable<RdfStatement> statements) {
        Dataset ds = DatasetFactory.create();
        Map<String, Resource> bnodeMap = new HashMap<>();

        for (RdfStatement stmt : statements) {
            if (stmt == null) continue;
            IRITerm graph = stmt.getGraph();
            Model model;
            if (graph == null || RML.defaultGraph.getURI().equals(graph.uri())) {
                model = ds.getDefaultModel();
            } else {
                model = ds.getNamedModel(graph.uri());
            }

            BlankNodeOrIRI sub = stmt.getSubject();
            Resource s;
            if (sub instanceof IRITerm iri) {
                s = ResourceFactory.createResource(iri.uri());
            } else if (sub instanceof BlankNodeTerm bnode) {
                s = bnodeMap.computeIfAbsent(bnode.id(), k -> ResourceFactory.createResource());
            } else {
                continue;
            }

            if (stmt.getPredicate() == null) continue;
            Property p = ResourceFactory.createProperty(stmt.getPredicate().uri());

            Term obj = stmt.getObject();
            RDFNode o;
            switch (obj) {
                case IRITerm iriTerm -> o = ResourceFactory.createResource(iriTerm.uri());
                case BlankNodeTerm blankNodeTerm -> o = bnodeMap.computeIfAbsent(blankNodeTerm.id(), k -> ResourceFactory.createResource());
                case LiteralTerm lit -> {
                    if (lit.language() != null && !lit.language().isBlank()) {
                        o = ResourceFactory.createLangLiteral(lit.value(), lit.language());
                    } else if (lit.datatype() != null && !lit.datatype().uri().isBlank()) {
                        o = ResourceFactory.createTypedLiteral(lit.value(), new BaseDatatype(lit.datatype().uri()));
                    } else {
                        o = ResourceFactory.createTypedLiteral(lit.value());
                    }
                }
                case null, default -> {
                    continue;
                }
            }
            model.add(s, p, o);
        }

        return ds;
    }

    public static String toNQuads(Dataset dataset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, dataset, Lang.NQUADS);
        return out.toString(StandardCharsets.UTF_8);
    }

    public static String toTriG(Dataset dataset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, dataset, Lang.TRIG);
        return out.toString(StandardCharsets.UTF_8);
    }
}
