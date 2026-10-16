package burp.parse;

import burp.ls.LogicalSourceFactory;
import burp.model.*;
import burp.model.gathermap.GatherMap;
import burp.model.lv.ExpressionField;
import burp.model.lv.LogicalView;
import burp.parse.loader.*;
import burp.parse.turtleprov.ProvStore;
import burp.parse.turtleprov.RDF12Converter;
import burp.parse.turtleprov.TurtleProvParser;
import burp.reporting.*;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotNotFoundException;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;

/**
 * RML Mapping Loader that configures ReflectiveRdfObjectLoader for RML specifications,
 * handles Turtle syntax provenance, SPARQL normalization updates, SHACL shape validation,
 * and parent-child AST tree wiring.
 */
@NullMarked
public class RmlLoader {

    private static final Path EMPTY_PATH = Path.of("");

    private final ReflectiveRdfObjectLoader reflectiveLoader = new ReflectiveRdfObjectLoader();

    private @Nullable Path mappingPath;
    private @Nullable String mappingContent;
    private @Nullable Model mappingModel;

    private Path mappingDirectory = EMPTY_PATH;
    private Path currentDirectory = EMPTY_PATH;
    private @Nullable RmlExecutionReport report;
    private ValidationMode validationMode = ValidationMode.STRICT;
    private Map<String, String> prefixMap = new HashMap<>();

    public Map<String, String> getPrefixMap() {
        return prefixMap;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    protected List<Path> mappingPaths = new ArrayList<>();

    public RmlLoader(Path mappingPath) {
        this.mappingPath = mappingPath;
        this.mappingPaths.add(mappingPath);
        Path abs = mappingPath.toAbsolutePath().normalize();
        this.mappingDirectory = abs.getParent() != null ? abs.getParent() : EMPTY_PATH;
        configureLoader();
    }

    public RmlLoader(List<Path> mappingPaths) {
        this.mappingPaths = new ArrayList<>(mappingPaths);
        if (!mappingPaths.isEmpty()) {
            this.mappingPath = mappingPaths.getFirst();
            Path abs = this.mappingPath.toAbsolutePath().normalize();
            this.mappingDirectory = abs.getParent() != null ? abs.getParent() : EMPTY_PATH;
        }
        configureLoader();
    }

    public RmlLoader(String mappingContent) {
        this.mappingContent = mappingContent;
        configureLoader();
    }

    public RmlLoader(Model mappingModel) {
        this.mappingModel = mappingModel;
        configureLoader();
    }

    public RmlLoader withCurrentDirectory(Path currentDirectory) {
        this.currentDirectory = currentDirectory;
        updateContext();
        return this;
    }

    public RmlLoader withMappingPath(Path mappingPath) {
        this.mappingPath = mappingPath;
        if (this.mappingDirectory.equals(EMPTY_PATH)) {
            Path abs = mappingPath.toAbsolutePath().normalize();
            this.mappingDirectory = abs.getParent() != null ? abs.getParent() : EMPTY_PATH;
        }
        updateContext();
        return this;
    }

    public RmlLoader withMappingDirectory(Path mappingDirectory) {
        this.mappingDirectory = mappingDirectory;
        updateContext();
        return this;
    }

    public RmlLoader withReport(@Nullable RmlExecutionReport report) {
        this.report = report;
        updateContext();
        return this;
    }

    public RmlLoader withValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
        updateContext();
        return this;
    }

    private void updateContext() {
        reflectiveLoader.setContext(new RmlLoaderContext(mappingDirectory, currentDirectory, report, validationMode));
    }

    private void configureLoader() {
        reflectiveLoader.setContext(new RmlLoaderContext(mappingDirectory, currentDirectory, report, validationMode));

        reflectiveLoader.registerTypeDecider(new ObjectMapTypeDecider());
        reflectiveLoader.registerTypeDecider(new ExpressionTypeDecider());
        reflectiveLoader.registerTypeDecider(new LogicalSourceTypeDecider());
        reflectiveLoader.registerTypeDecider(new FieldTypeDecider());

        reflectiveLoader.registerCustomMapper(AbstractLogicalSource.class, (model, resource, loader) -> {
            if (resource.hasProperty(RML.viewOn)) {
                return loader.map(model, resource, LogicalView.class);
            }
            RmlLoaderContext ctx = (RmlLoaderContext) loader.getContext();
            LogicalSource ls = LogicalSourceFactory.create(resource, ctx);
            recordLogicalSourceStatements(resource, loader);
            return ls;
        });

        reflectiveLoader.registerCustomMapper(Template.class, (model, r, loader) -> {
            Statement stmt = r.getProperty(RML.template);
            if (stmt != null && stmt.getObject().isLiteral()) {
                loader.recordAccessedStatement(stmt);
                RmlLoaderContext ctx = (RmlLoaderContext) loader.getContext();
                ValidationMode mode = ctx != null ? ctx.validationMode() : ValidationMode.STRICT;
                RmlExecutionReport rep = ctx != null ? ctx.report() : null;
                Template template = TemplateParser.parse(stmt.getObject().asLiteral().getString(), stmt, rep, mode);
                template.putOrigin("template", new Origin(stmt, StatementPart.Predicate, StatementPart.Object));
                return template;
            }
            return null;
        });

        reflectiveLoader.registerCustomMapper(RMLTarget.class, (model, targetRes, loader) -> {
            recordStatementsRecursively(targetRes, new HashSet<>(), loader);
            if (targetRes.hasProperty(RML.path)) {
                String path = targetRes.getProperty(RML.path).getObject().asLiteral().getString();
                Resource root = targetRes.getPropertyResourceValue(RML.root);
                if (root == null) root = RML.CurrentWorkingDirectory;
                return new FilePathTarget(path, root);
            }
            Property dcatDataDump = ResourceFactory.createProperty("http://www.w3.org/ns/dcat#dataDump");
            if (targetRes.hasProperty(dcatDataDump)) {
                RDFNode dumpNode = targetRes.getProperty(dcatDataDump).getObject();
                String path = dumpNode.isURIResource() ? dumpNode.asResource().getURI() : dumpNode.asLiteral().getString();
                if (path.startsWith("file://.")) path = path.substring(8);
                else if (path.startsWith("file://")) path = path.substring(7);
                return new FilePathTarget(path, RML.CurrentWorkingDirectory);
            }
            Property voidDataDump = ResourceFactory.createProperty("http://rdfs.org/ns/void#dataDump");
            if (targetRes.hasProperty(voidDataDump)) {
                RDFNode dumpNode = targetRes.getProperty(voidDataDump).getObject();
                String path = dumpNode.isURIResource() ? dumpNode.asResource().getURI() : dumpNode.asLiteral().getString();
                if (path.startsWith("file://.")) path = path.substring(8);
                else if (path.startsWith("file://")) path = path.substring(7);
                return new FilePathTarget(path, RML.CurrentWorkingDirectory);
            }
            return null;
        });

        reflectiveLoader.registerPostProcessor(LogicalView.class, (model, resource, lv, loader) -> lv.initialize());
        reflectiveLoader.registerPostProcessor(ExpressionField.class, (model, resource, ef, loader) -> {
            if (ef.fieldExpressionMap == null) {
                Property fieldExprProp = ResourceFactory.createProperty(Rml.NS + "fieldExpressionMap");
                if (resource.hasProperty(fieldExprProp)) {
                    Resource fem = resource.getPropertyResourceValue(fieldExprProp);
                    if (fem != null) {
                        loader.recordAccessedStatement(resource.getProperty(fieldExprProp));
                        ef.fieldExpressionMap = loader.map(model, fem, ConcreteExpressionMap.class);
                    }
                } else {
                    ef.fieldExpressionMap = loader.map(model, resource, ConcreteExpressionMap.class);
                }
            }
        });
        reflectiveLoader.registerPostProcessor(TermMap.class, (model, resource, instance, loader) -> {
            if (instance.gatherMap == null && resource.hasProperty(RML.gather)) {
                Statement gatherStmt = resource.getProperty(RML.gather);
                loader.recordAccessedStatement(gatherStmt);
                instance.gatherMap = loader.map(model, resource, GatherMap.class);
                if (gatherStmt != null) {
                    Origin origin = new Origin(gatherStmt, StatementPart.Predicate, StatementPart.Object);
                    instance.gatherMap.addExtraOrigin(origin);
                    instance.putOrigin("gatherMap", origin);
                    instance.putOrigin("gather", origin);
                }
            }
        });
        reflectiveLoader.registerPostProcessor(TriplesMap.class, (model, resource, tm, loader) -> {
            Resource ls = resource.getPropertyResourceValue(RML.logicalSource);
            if (ls != null) {
                ls.listProperties(RML.logicalTarget).forEachRemaining(s -> {
                    loader.recordAccessedStatement(s);
                    LogicalTarget lt = loader.map(model, s.getResource(), LogicalTarget.class);
                    if (lt != null) {
                        tm.getLogicalTargets().add(lt);
                    }
                });
            }
            RmlLoaderContext ctx = (RmlLoaderContext) loader.getContext();
            ValidationMode mode = ctx != null ? ctx.validationMode() : ValidationMode.STRICT;
            RmlExecutionReport rep = ctx != null ? ctx.report() : null;
            PlanWiring.wire(tm, mode, rep);
        });
    }

    public ReflectiveRdfObjectLoader getReflectiveLoader() {
        return reflectiveLoader;
    }

    public @Nullable Model getMappingModel() {
        return mappingModel;
    }

    public @Nullable RmlExecutionReport getReport() {
        return report;
    }

    // --- High Level Load Methods ---

    public List<TriplesMap> loadMapping() {
        Model model;
        if (mappingModel != null) {
            model = mappingModel;
        } else if (mappingContent != null) {
            model = parseStringToModel(mappingContent, mappingPath);
        } else if (!mappingPaths.isEmpty()) {
            if (mappingPaths.size() == 1) {
                model = parsePathToModel(mappingPaths.getFirst());
            } else {
                model = ModelFactory.createDefaultModel();
                for (Path p : mappingPaths) {
                    Model m = parsePathToModel(p);
                    model.add(m);
                    if (m.getNsPrefixMap() != null) {
                        this.prefixMap.putAll(m.getNsPrefixMap());
                    }
                }
            }
        } else if (mappingPath != null) {
            model = parsePathToModel(mappingPath);
        } else {
            throw new BurpException(new RmlError("No mapping source specified for loading.", null, RER.ConfigurationError));
        }
        this.mappingModel = model;

        if (this.prefixMap.isEmpty()) {
            this.prefixMap = new HashMap<>(model.getNsPrefixMap());
        }

        if (validationMode == ValidationMode.STRICT) {
            boolean valid = RmlShaclValidator.validate(model, this.report, this.mappingPath);
            if (!valid) {
                if (this.report != null && !this.report.getErrors().isEmpty()) {
                    throw new BurpException(this.report.getErrors().getFirst());
                } else {
                    //TODO Add origin from report transformation
                    throw new BurpException(new RmlError("Mapping did not satisfy shapes.", null, RER.MappingError));
                }
            }
        } else if (validationMode == ValidationMode.LENIENT) {
            RmlShaclValidator.validate(model, this.report, this.mappingPath);
        }

        return loadTriplesMaps(model);
    }

    public MappingDocument loadMappingDocument() {
        List<TriplesMap> triplesMaps = loadMapping();
        MappingDocument document = new MappingDocument(triplesMaps, prefixMap);
        Set<LogicalView> logicalViews = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TriplesMap tm : triplesMaps) {
            if (tm.logicalSource instanceof LogicalView lv) logicalViews.add(lv);
            tm.descendants(LogicalView.class).forEach(logicalViews::add);
        }
        for (Object obj : reflectiveLoader.getCache().values()) {
            if (obj instanceof LogicalView lv) logicalViews.add(lv);
        }
        LogicalView.validateLogicalViews(logicalViews, this.report, this.validationMode);
        return document;
    }

    private Model parsePathToModel(Path path) throws BurpException {
        Path absMappingFile = path.toAbsolutePath().normalize();
        if (this.mappingDirectory.equals(EMPTY_PATH)) {
            this.mappingDirectory = absMappingFile.getParent() != null ? absMappingFile.getParent() : EMPTY_PATH;
            updateContext();
        }

        Lang guessType = RDFDataMgr.determineLang(path.toString(), null, null);
        if (guessType == Lang.TURTLE) {
            ProvStore provStore;
            try {
                provStore = TurtleProvParser.parseTurtleFromPath(path);
            } catch (IOException e) {
                throw new BurpException(new RmlError("Could not load mapping file: " + path, null, RER.ConfigurationError, e));
            }
            if (!provStore.getSyntaxErrors().isEmpty()) {
                var rmlSyntaxErrors = provStore.getSyntaxErrors().stream().map(error -> {
                    TextFilePointer tfp = new TextFilePointer(path, error.range());
                    Origin origin = new Origin(List.of(tfp));
                    return new RmlError(error.message(), origin, RER.RDFMappingSyntaxError);
                }).toList();
                if (this.report != null) {
                    this.report.getErrors().addAll(rmlSyntaxErrors);
                }
                if (validationMode == ValidationMode.STRICT) {
                    throw new BurpException(rmlSyntaxErrors.getFirst());
                }
            }
            this.prefixMap = new HashMap<>(provStore.getPrefixes());
            return RDF12Converter.toModel(provStore);
        } else {
            try {
                Model model = RDFDataMgr.loadModel(path.toString());
                this.prefixMap = new HashMap<>(model.getNsPrefixMap());
                return model;
            } catch (RiotNotFoundException e) {
                throw new BurpException(new RmlError("Could not load mapping file: " + path, null, RER.ConfigurationError, e));
            }

        }
    }

    private Model parseStringToModel(String content, @Nullable Path path) {
        try {
            ProvStore provStore = TurtleProvParser.parseTurtleFromString(content, path != null ? path.toUri().toString() : null);
            if (!provStore.getSyntaxErrors().isEmpty()) {
                var rmlSyntaxErrors = provStore.getSyntaxErrors().stream().map(error -> {
                    TextFilePointer tfp = path != null ? new TextFilePointer(path, error.range()) : null;
                    Origin origin = tfp != null ? new Origin(List.of(tfp)) : null;
                    return new RmlError(error.message(), origin, RER.RDFMappingSyntaxError);
                }).toList();
                if (this.report != null) {
                    this.report.getErrors().addAll(rmlSyntaxErrors);
                }
                if (validationMode == ValidationMode.STRICT) {
                    throw new BurpException(rmlSyntaxErrors.getFirst());
                }
            }
            this.prefixMap = new HashMap<>(provStore.getPrefixes());
            return RDF12Converter.toModel(provStore);
        } catch (BurpException e) {
            throw e;
        } catch (Exception e) {
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, new StringReader(content), null, Lang.TURTLE);
            this.prefixMap = new HashMap<>(model.getNsPrefixMap());
            return model;
        }
    }

    private List<TriplesMap> loadTriplesMaps(Model model) {
        Set<Resource> tmResources = new LinkedHashSet<>();
        model.listSubjectsWithProperty(RDF.type, ResourceFactory.createResource(Rml.TriplesMap))
                .forEachRemaining(tmResources::add);
        model.listSubjectsWithProperty(RML.logicalSource)
                .forEachRemaining(tmResources::add);
        model.listSubjectsWithProperty(RML.subjectMap)
                .forEachRemaining(tmResources::add);
        model.listSubjectsWithProperty(ResourceFactory.createProperty(Rml.subject))
                .forEachRemaining(tmResources::add);

        List<TriplesMap> triplesMaps = new ArrayList<>();
        for (Resource tmRes : tmResources) {
            TriplesMap tm = reflectiveLoader.map(model, tmRes, TriplesMap.class);
            triplesMaps.add(tm);
            for (Statement s : tmRes.listProperties(RDF.type).toList()) {
                if (s.getObject().isURIResource() && s.getObject().asResource().getURI().equals(Rml.TriplesMap)) {
                    reflectiveLoader.recordAccessedStatement(s);
                }
            }
        }
        return triplesMaps;
    }

    // --- Helpers ---

    private void recordLogicalSourceStatements(Resource ls, ReflectiveRdfObjectLoader loader) {
        Set<Resource> visited = new HashSet<>();
        recordStatementsRecursively(ls, visited, loader);
    }

    private void recordStatementsRecursively(Resource res, Set<Resource> visited, ReflectiveRdfObjectLoader loader) {
        if (!visited.add(res)) {
            return;
        }
        StmtIterator it = res.listProperties();
        while (it.hasNext()) {
            Statement s = it.nextStatement();
            loader.recordAccessedStatement(s);
            if (s.getObject().isResource()) {
                recordStatementsRecursively(s.getObject().asResource(), visited, loader);
            }
        }
    }
}
