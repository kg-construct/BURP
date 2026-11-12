package burp.dev.rml.service.ontology;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.*;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/// In-memory ontology model and index backed by modern Jena OntAPI.
@NullMarked
public class OntologyRegistry {

    private static final Property SCHEMA_DOMAIN_INCLUDES_HTTP = ResourceFactory.createProperty("http://schema.org/domainIncludes");
    private static final Property SCHEMA_DOMAIN_INCLUDES_HTTPS = ResourceFactory.createProperty("https://schema.org/domainIncludes");
    private static final Property SCHEMA_RANGE_INCLUDES_HTTP = ResourceFactory.createProperty("http://schema.org/rangeIncludes");
    private static final Property SCHEMA_RANGE_INCLUDES_HTTPS = ResourceFactory.createProperty("https://schema.org/rangeIncludes");

    private final OntModel ontModel;

    public OntologyRegistry() {
        this(OntModelFactory.createModel(OntSpecification.OWL2_DL_MEM_RDFS_INF));
    }

    public OntologyRegistry(OntModel ontModel) {
        this.ontModel = ontModel;
    }

    public OntModel getOntModel() {
        return ontModel;
    }

    public void loadOntology(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Lang lang = RDFDataMgr.determineLang(path.toString(), null, Lang.TURTLE);
            RDFDataMgr.read(ontModel, in, lang);
        }
    }

    public void loadOntology(InputStream in, Lang lang) {
        RDFDataMgr.read(ontModel, in, lang);
    }

    public void loadOntology(String content, Lang lang) {
        try (StringReader reader = new StringReader(content)) {
            RDFDataMgr.read(ontModel, reader, null, lang);
        }
    }

    /// Resolves all classes and superclasses for the given target classes, mapped to their inheritance distance.
    /// Distance 0 = the class itself, 1 = direct superclass, 2 = grandparent, etc.
    public Map<Resource, Integer> computeClassHierarchyDistances(Set<Resource> targetClasses) {
        Map<Resource, Integer> distances = new HashMap<>();
        Queue<Resource> queue = new ArrayDeque<>();

        for (Resource target : targetClasses) {
            if (target.isURIResource()) {
                distances.put(target, 0);
                queue.add(target);
            }
        }

        var baseModel = ontModel.getBaseModel() != null ? ontModel.getBaseModel() : ontModel;

        while (!queue.isEmpty()) {
            Resource current = queue.poll();
            int currentDist = distances.get(current);

            baseModel.listStatements(current, RDFS.subClassOf, (RDFNode) null).forEachRemaining(stmt -> {
                if (stmt.getObject().isURIResource()) {
                    Resource parent = stmt.getObject().asResource();
                    if (!distances.containsKey(parent) || distances.get(parent) > currentDist + 1) {
                        distances.put(parent, currentDist + 1);
                        queue.add(parent);
                    }
                }
            });
        }

        return distances;
    }

    /// Returns all applicable properties for the given target classes, including inherited properties.
    public List<PropertyCandidate> getApplicableProperties(Set<Resource> targetClasses, Map<String, String> prefixMap) {
        if (targetClasses.isEmpty()) {
            return getAllProperties(prefixMap);
        }

        Map<Resource, Integer> hierarchyDistances = computeClassHierarchyDistances(targetClasses);
        Map<Resource, PropertyCandidate> candidates = new LinkedHashMap<>();

        for (var entry : hierarchyDistances.entrySet()) {
            Resource classRes = entry.getKey();
            int distance = entry.getValue();

            for (Resource prop : findPropertiesForClass(classRes)) {
                if (!candidates.containsKey(prop) || candidates.get(prop).inheritanceDistance() > distance) {
                    PropertyCandidate pc = buildCandidate(prop, distance, classRes, prefixMap);
                    candidates.put(prop, pc);
                }
            }
        }

        return new ArrayList<>(candidates.values());
    }

    /// Returns all properties declared in the ontology as candidates with distance 0.
    public List<PropertyCandidate> getAllProperties(Map<String, String> prefixMap) {
        Set<Resource> props = new LinkedHashSet<>();
        ontModel.listStatements(null, RDF.type, OWL.ObjectProperty).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });
        ontModel.listStatements(null, RDF.type, OWL.DatatypeProperty).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });
        ontModel.listStatements(null, RDF.type, RDF.Property).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });
        ontModel.listStatements(null, RDFS.domain, (RDFNode) null).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });

        List<PropertyCandidate> candidates = new ArrayList<>();
        for (Resource prop : props) {
            candidates.add(buildCandidate(prop, 0, null, prefixMap));
        }
        return candidates;
    }

    /// Checks validation constraints for a given predicate and subject classes.
    public PropertyConstraint getPropertyConstraint(Set<Resource> targetClasses, Resource predicate) {
        if (!predicate.isURIResource()) {
            return new PropertyConstraint(predicate, false, true, Collections.emptySet(), null, false, false);
        }

        OntProperty ontProp = findOntProperty(predicate.getURI());
        boolean isKnown = (ontProp != null)
                || ontModel.contains(predicate, RDF.type, OWL.ObjectProperty)
                || ontModel.contains(predicate, RDF.type, OWL.DatatypeProperty)
                || ontModel.contains(predicate, RDF.type, RDF.Property)
                || ontModel.contains(predicate, RDFS.domain, (RDFNode) null)
                || ontModel.contains(predicate, RDFS.range, (RDFNode) null);

        if (!isKnown) {
            return new PropertyConstraint(predicate, false, true, Collections.emptySet(), null, false, false);
        }

        Set<Resource> declaredDomains = getDeclaredDomains(predicate, ontProp);
        boolean isDomainCompatible = true;
        if (!declaredDomains.isEmpty() && !targetClasses.isEmpty()) {
            Map<Resource, Integer> hierarchy = computeClassHierarchyDistances(targetClasses);
            isDomainCompatible = declaredDomains.stream().anyMatch(hierarchy::containsKey);
        }

        Resource expectedRange = getDeclaredRange(predicate, ontProp);
        boolean isObject = isObjectProperty(predicate, ontProp);
        boolean isDatatype = isDatatypeProperty(predicate, ontProp);

        return new PropertyConstraint(predicate, true, isDomainCompatible, declaredDomains, expectedRange, isObject, isDatatype);
    }

    /// Returns all declared classes in the ontology.
    public List<Resource> getAllClasses() {
        Set<Resource> classes = new LinkedHashSet<>();
        ontModel.classes().filter(RDFNode::isURIResource).forEach(classes::add);
        ontModel.listStatements(null, RDF.type, OWL.Class).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) classes.add(s.getSubject());
        });
        ontModel.listStatements(null, RDF.type, RDFS.Class).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) classes.add(s.getSubject());
        });
        return new ArrayList<>(classes);
    }

    public @Nullable OntProperty findOntProperty(String uri) {
        OntProperty prop = ontModel.getObjectProperty(uri);
        if (prop != null) return prop;
        prop = ontModel.getDataProperty(uri);
        if (prop != null) return prop;
        return ontModel.getAnnotationProperty(uri);
    }

    private Set<Resource> findPropertiesForClass(Resource classRes) {
        Set<Resource> props = new LinkedHashSet<>();

        ontModel.listStatements(null, RDFS.domain, classRes).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });
        ontModel.listStatements(null, SCHEMA_DOMAIN_INCLUDES_HTTP, classRes).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });
        ontModel.listStatements(null, SCHEMA_DOMAIN_INCLUDES_HTTPS, classRes).forEachRemaining(s -> {
            if (s.getSubject().isURIResource()) props.add(s.getSubject());
        });

        return props;
    }

    private PropertyCandidate buildCandidate(Resource prop, int distance, @Nullable Resource definedOnClass, Map<String, String> prefixMap) {
        String uri = prop.getURI();
        String compact = RmlEvaluationResultFormatter.compactIri(uri, prefixMap);
        OntProperty ontProp = findOntProperty(uri);

        String label = getAnnotation(prop, RDFS.label);
        String comment = getAnnotation(prop, RDFS.comment);
        Resource range = getDeclaredRange(prop, ontProp);
        boolean isObj = isObjectProperty(prop, ontProp);
        boolean isData = isDatatypeProperty(prop, ontProp);

        return new PropertyCandidate(prop, compact, label, comment, range, isObj, isData, distance, definedOnClass);
    }

    private @Nullable String getAnnotation(Resource res, Property annotationProp) {
        var stmt = ontModel.getProperty(res, annotationProp);
        if (stmt != null && stmt.getObject().isLiteral()) {
            return stmt.getObject().asLiteral().getString();
        }
        return null;
    }

    private Set<Resource> getDeclaredDomains(Resource prop, @Nullable OntProperty ontProp) {
        Set<Resource> domains = new LinkedHashSet<>();
        if (ontProp != null) {
            ontProp.domains().filter(RDFNode::isURIResource).forEach(domains::add);
        }
        ontModel.listStatements(prop, RDFS.domain, (RDFNode) null).forEachRemaining(s -> {
            if (s.getObject().isURIResource()) domains.add(s.getObject().asResource());
        });
        ontModel.listStatements(prop, SCHEMA_DOMAIN_INCLUDES_HTTP, (RDFNode) null).forEachRemaining(s -> {
            if (s.getObject().isURIResource()) domains.add(s.getObject().asResource());
        });
        ontModel.listStatements(prop, SCHEMA_DOMAIN_INCLUDES_HTTPS, (RDFNode) null).forEachRemaining(s -> {
            if (s.getObject().isURIResource()) domains.add(s.getObject().asResource());
        });
        return domains;
    }

    private @Nullable Resource getDeclaredRange(Resource prop, @Nullable OntProperty ontProp) {
        if (ontProp != null) {
            Optional<? extends Resource> first = ontProp.ranges().filter(RDFNode::isURIResource).findFirst();
            if (first.isPresent()) {
                return first.get();
            }
        }
        var stmt = ontModel.getProperty(prop, RDFS.range);
        if (stmt != null && stmt.getObject().isURIResource()) {
            return stmt.getObject().asResource();
        }
        var schemaStmt = ontModel.getProperty(prop, SCHEMA_RANGE_INCLUDES_HTTP);
        if (schemaStmt == null) {
            schemaStmt = ontModel.getProperty(prop, SCHEMA_RANGE_INCLUDES_HTTPS);
        }
        if (schemaStmt != null && schemaStmt.getObject().isURIResource()) {
            return schemaStmt.getObject().asResource();
        }
        return null;
    }

    private boolean isObjectProperty(Resource prop, @Nullable OntProperty ontProp) {
        if (ontProp instanceof OntObjectProperty) return true;
        return ontModel.contains(prop, RDF.type, OWL.ObjectProperty);
    }

    private boolean isDatatypeProperty(Resource prop, @Nullable OntProperty ontProp) {
        if (ontProp instanceof OntDataProperty) return true;
        return ontModel.contains(prop, RDF.type, OWL.DatatypeProperty);
    }
}
