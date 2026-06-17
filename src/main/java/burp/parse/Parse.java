package burp.parse;

import burp.Main;
import burp.ls.LogicalSourceFactory;
import burp.model.*;
import burp.model.fnml.FunctionExecution;
import burp.model.fnml.ReturnMap;
import burp.model.gathermap.GatherMap;
import burp.model.lv.*;
import burp.model.rdf.BlankNodeTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.parse.turtleprov.ProvStore;
import burp.parse.turtleprov.RDF12Converter;
import burp.parse.turtleprov.TurtleProvParser;
import burp.reporting.*;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.sparql.path.*;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.util.FileUtils;

import java.nio.file.Path;
import java.util.*;

import static burp.reporting.StatementPart.*;

public class Parse {

    public final Map<Resource, TriplesMap> triplesMaps = new HashMap<>();
    public final Map<Resource, LogicalView> logicalViews = new HashMap<>();
    public final Map<Resource, LogicalTarget> logicalTargets = new HashMap<>();

    private Path mappingDirectory = null;
    private Path mappingFile = null;
    private Path currentDirectory = null;
    private Model mapping = null;
    private ProvStore provStore = null;

    private Dataset parseTurtleFromFile(Path turtleFile) throws Exception {
        provStore = TurtleProvParser.parseTurtleFromPath(turtleFile);
        if (!provStore.getSyntaxErrors().isEmpty()) {
            var rmlSyntaxError = provStore.getSyntaxErrors().stream().map(error -> {
                TextFilePointer tfp = new TextFilePointer(turtleFile, error.range);
                Origin origin = new Origin(null, List.of(tfp));
                return new RmlError(error.message, origin, RER.RDFMappingSyntaxError);
            }).toList();
            if (Main.report != null) {
                Main.report.getErrors().addAll(rmlSyntaxError);
            }
            throw new BurpException(rmlSyntaxError.getFirst());
        }
        Model m = RDF12Converter.toModel(provStore);
        return DatasetFactory.create(m);
    }

    public List<TriplesMap> parseMappingFile(Path mappingPath, Path currentDirectory) throws Exception {
        this.mappingFile = mappingPath.toAbsolutePath().normalize();
        this.mappingDirectory = this.mappingFile.getParent();
        this.currentDirectory = currentDirectory;

        Lang guessType = RDFDataMgr.determineLang(mappingPath.toString(), null, null);

        if (guessType == Lang.TURTLE) {
            Dataset dataset = parseTurtleFromFile(mappingPath);
            mapping = dataset.getDefaultModel();
        } else {
            mapping = RDFDataMgr.loadModel(mappingPath.toString());
        }

        if (!isValid(mapping)) {
            throw new BurpException(new RmlError("Mapping did not satisfy shapes.", null, RER.MappingError, null));
        }

        // Replace rml:subject, rml:object, ... with constant getExpression() maps
        normalizeConstantsUpdate(mapping);

        // Look for the triples maps
        List<Resource> list = mapping.listSubjectsWithProperty(RML.logicalSource).toList();

        // Process each triples map
        for (Resource r : list) {
            TriplesMap tm = triplesMaps.computeIfAbsent(r, TriplesMap::new);

            Resource ls = r.getPropertyResourceValue(RML.logicalSource);
            Statement lsStmt = r.getProperty(RML.logicalSource);
            tm.logicalSource = prepareLogicalSource(ls);

            ls.listProperties(RML.logicalTarget).forEachRemaining(s -> tm.getLogicalTargets().add(prepareLogicalTarget(s.getResource())));

            r.listProperties(RML.logicalTarget).forEachRemaining(s -> tm.getLogicalTargets().add(prepareLogicalTarget(s.getResource())));

            List<Statement> subjectMapList = r.listProperties(RML.subjectMap).toList();
            if (subjectMapList.isEmpty()) {
                Main.report.getErrors().add(
                        new RmlError(
                                "No subject maps in " + tm,
                                new Origin(lsStmt, Subject, null),
                                RER.MappingError,
                                null
                        )
                );
                continue;
            }
            if (subjectMapList.size() > 1) {
                List<StatementParts> originStatements = subjectMapList.stream()
                        .map(StatementParts::fromPredicateObject)
                        .toList();
                Main.report.getErrors().add(
                        new RmlError(
                                "Multiple subject maps in " + tm,
                                new Origin(null, originStatements),
                                RER.MappingError,
                                null
                        )
                );
            }
            Resource sm = r.getPropertyResourceValue(RML.subjectMap);
            tm.subjectMap = prepareSubjectMap(sm);

            if (r.hasProperty(RML.baseIRI)) {
                tm.baseIRI = r.getPropertyResourceValue(RML.baseIRI).getURI();
            }

            r.listProperties(RML.predicateObjectMap).forEachRemaining(s -> {
                PredicateObjectMap pom = preparePredicateObjectMap(s.getObject().asResource());
                tm.predicateObjectMaps.add(pom);
            });
        }

        return new ArrayList<>(triplesMaps.values());
    }

    private boolean isValid(Model mapping) {
        Model core = ModelFactory.createDefaultModel();
        core.read(Parse.class.getResourceAsStream("/shapes/core.ttl"), "urn:dummy", FileUtils.langTurtle);
        core.read(Parse.class.getResourceAsStream("/shapes/cc.ttl"), "urn:dummy", FileUtils.langTurtle);
        core.read(Parse.class.getResourceAsStream("/shapes/lv.ttl"), "urn:dummy", FileUtils.langTurtle);
        core.read(Parse.class.getResourceAsStream("/shapes/io.ttl"), "urn:dummy", FileUtils.langTurtle);

        ValidationReport report = ShaclValidator.get().validate(core.getGraph(), mapping.getGraph());
        if (!report.conforms()) {
            report.getEntries().forEach(vr -> {
                List<StatementParts> focusOrigins = extractStatementsFromShaclViolation(vr, mapping);
                Main.report.getErrors().add(
                        new RmlError(
                                vr.message() + " \nNode=" + vr.focusNode() + "\nPath=" + vr.resultPath() + "\nValue: " + vr.value() + "\n",
                                new Origin(null, focusOrigins.isEmpty() ? null : focusOrigins),
                                RER.MappingError,
                                null
                        )
                );
            });
            return false;
        }

        return true;
    }

    private void normalizeConstantsUpdate(Model mapping) {
        String conditionShortcutExpand = """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX idlab-fn: <https://w3id.org/imec/idlab/function#>
                
                DELETE {
                    ?map rml:condition ?condition .
                    ?map ?prop ?value .
                }
                INSERT {
                    ?map rml:functionExecution [
                        rml:function idlab-fn:IF ;
                        rml:input [
                            rml:parameter idlab-fn:boolParameter ;
                            rml:inputValueMap ?condition
                        ] , [
                            rml:parameter idlab-fn:expressionParameter ;
                            rml:inputValueMap [
                                ?prop ?value
                            ]
                        ]
                    ] .
                }
                WHERE {
                    ?map rml:condition ?condition .
                    ?map ?prop ?value .
                    VALUES ?prop { rml:constant rml:reference rml:template rml:functionExecution }
                }
                """;

        String constructTermTypes = """
                PREFIX r: <http://w3id.org/rml/>
                INSERT { ?x r:constant ?y ; r:termType ?z . }
                WHERE {
                    ?x r:constant ?y.
                    BIND(IF(ISLITERAL(?y), r:Literal, IF(ISIRI(?y), r:IRI, r:BlankNode)) AS ?z)
                }
                """;

        List<String> updateQueries = Arrays.asList(
                conditionShortcutExpand,
                expandShortcut(RML.subjectMap, RML.constant, ResourceFactory.createProperty(RML.NS + "subject")),
                expandShortcut(RML.objectMap, RML.constant, ResourceFactory.createProperty(RML.NS + "object")),
                expandShortcut(RML.predicateMap, RML.constant, ResourceFactory.createProperty(RML.NS + "predicate")),
                expandShortcut(RML.graphMap, RML.constant, ResourceFactory.createProperty(RML.NS + "graph")),

                expandShortcut(RML.languageMap, RML.constant, ResourceFactory.createProperty(RML.NS + "language")),
                expandShortcut(RML.datatypeMap, RML.constant, ResourceFactory.createProperty(RML.NS + "datatype")),

                expandShortcut(RML.childMap, RML.reference, ResourceFactory.createProperty(RML.NS + "child")),
                expandShortcut(RML.parentMap, RML.reference, ResourceFactory.createProperty(RML.NS + "parent")),

                expandShortcut(RML.returnMap, RML.constant, ResourceFactory.createProperty(RML.NS + "return")),
                expandShortcut(RML.functionMap, RML.constant, ResourceFactory.createProperty(RML.NS + "function")),
                expandShortcut(RML.parameterMap, RML.constant, ResourceFactory.createProperty(RML.NS + "parameter")),
                expandShortcut(RML.inputValueMap, RML.constant, ResourceFactory.createProperty(RML.NS + "inputValue")),
                constructTermTypes,
                constructImplicitTermTypeQuery(RML.subjectMap),
                constructImplicitTermTypeQuery(RML.graphMap),
                constructImplicitTermTypeQuery(RML.objectMap)
        );

        for (String query : updateQueries) {
            try {
                UpdateRequest update = UpdateFactory.create(query);
                UpdateAction.execute(update, mapping);
            } catch (QueryParseException e) {
                throw new BurpException(
                        new RmlError(
                                "Normalize mapping failed: " + query,
                                null,
                                RER.UnexpectedError,
                                e
                        )
                );
            }
        }
    }

    public String expandShortcut(Property mapType, Property expressionType, Property mapTypeShort) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                """
                        INSERT { ?x ?mapType [ ?expressionType ?y ]. }
                        WHERE { ?x ?mapTypeShort ?y . }
                        """
        );
        pss.setIri("mapType", mapType.getURI());
        pss.setIri("expressionType", expressionType.getURI());
        pss.setIri("mapTypeShort", mapTypeShort.getURI());
        return pss.toString();
    }

    public String constructImplicitTermTypeQuery(Property mapType) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
                """
                        PREFIX rml: <http://w3id.org/rml/>
                        INSERT { ?x rml:termType rml:BlankNode }
                        WHERE {
                           [] ?mapType ?x .
                           OPTIONAL { ?x rml:template ?a }
                           OPTIONAL { ?x rml:reference ?b }
                           OPTIONAL { ?x rml:constant ?c }
                           OPTIONAL { ?x rml:functionExecution ?d }
                           FILTER(!BOUND(?a) && !BOUND(?b) && !BOUND(?c) && !BOUND(?d))
                        }
                        """
        );
        pss.setIri("mapType", mapType.getURI());
        return pss.toString();
    }

    private AbstractLogicalSource prepareLogicalSource(Resource ls) {
        if (ls.hasProperty(RML.viewOn)) {
            return prepareLogicalView(ls);
        }
        return LogicalSourceFactory.create(ls, mappingDirectory, currentDirectory);
    }

    private LogicalView prepareLogicalView(Resource ls) {
        try {
            Resource view = ls.getPropertyResourceValue(RML.viewOn);
            LogicalView lv = logicalViews.computeIfAbsent(ls, k -> new LogicalView());

            lv.logicalSource = prepareLogicalSource(view);

            ls.listProperties(RML.field).forEachRemaining(s -> lv.addField(prepareField(s.getObject().asResource())));

            ls.listProperties(RML.leftJoin).forEachRemaining(s -> lv.addJoin(prepareLeftJoin(s.getObject().asResource())));

            ls.listProperties(RML.innerJoin).forEachRemaining(s -> lv.addJoin(prepareInnerJoin(s.getObject().asResource())));

            return lv;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ViewJoin prepareLeftJoin(Resource resource) {
        return prepareViewJoin(JoinType.LEFT, resource);
    }

    private ViewJoin prepareInnerJoin(Resource resource) {
        return prepareViewJoin(JoinType.INNER, resource);
    }

    private ViewJoin prepareViewJoin(JoinType joinType, Resource resource) {
        ViewJoin viewJoin = new ViewJoin();
        viewJoin.joinType = joinType;

        Resource plv = resource.getRequiredProperty(RML.parentLogicalView).getObject().asResource();
        viewJoin.parentLogicalView = prepareLogicalView(plv);

        List<JoinCondition> conditions = new ArrayList<>();
        resource.listProperties(RML.joinCondition).forEachRemaining(s -> conditions.add(prepareJoinCondition(s)));
        viewJoin.joinConditions = conditions;

        resource.listProperties(RML.field).forEachRemaining(s -> viewJoin.addField(prepareField(s.getObject().asResource())));

        return viewJoin;
    }

    private SubjectMap prepareSubjectMap(Resource sm) {
        SubjectMap subjectMap = new SubjectMap();
        prepareExpression(sm, subjectMap);

        sm.listProperties(RML.clazz).forEachRemaining(s -> subjectMap.classes.add(s.getObject().asResource()));

        sm.listProperties(RML.graphMap).forEachRemaining(s -> {
            GraphMap gm = prepareGraphMap(s.getObject().asResource());
            subjectMap.graphMaps.add(gm);
        });

        Resource termType = sm.getPropertyResourceValue(RML.termType);
        if (termType != null) {
            subjectMap.termType = termType;
        } else if (hasNoTemplateReferenceConstantOrFunction(sm)) {
            subjectMap.termType = RML.BLANKNODE;
        }

        Resource gm = sm.getPropertyResourceValue(RML.gather);
        if (gm != null) {
            subjectMap.gatherMap = prepareGatherMap(sm);
        }

        return subjectMap;
    }

    private PredicateObjectMap preparePredicateObjectMap(Resource pom) {
        PredicateObjectMap predicateObjectMap = new PredicateObjectMap();

        pom.listProperties(RML.graphMap).forEachRemaining(s -> {
            GraphMap gm = prepareGraphMap(s.getObject().asResource());
            predicateObjectMap.graphMaps.add(gm);
        });

        pom.listProperties(RML.predicateMap).forEachRemaining(s -> {
            PredicateMap pm = preparePredicateMap(s.getObject().asResource());
            predicateObjectMap.predicateMaps.add(pm);
        });

        pom.listProperties(RML.objectMap).forEachRemaining(s -> {
            if (s.getObject().asResource().getProperty(RML.parentTriplesMap) == null) {
                ObjectMap om = prepareObjectMap(s.getObject().asResource());
                predicateObjectMap.objectMaps.add(om);
            } else {
                ReferencingObjectMap rom = prepareReferencingObjectMap(s.getObject().asResource());
                predicateObjectMap.objectMaps.add(rom);
            }
        });

        return predicateObjectMap;
    }

    private GraphMap prepareGraphMap(Resource r) {
        return prepareTermMapMinimal(r, new GraphMap());
    }

    private PredicateMap preparePredicateMap(Resource pm) {
        return prepareExpression(pm, new PredicateMap());
    }

    private <TM extends TermMap> TM prepareTermMapMinimal(Resource tmRdf, TM tm) {
        prepareExpression(tmRdf, tm);

        Resource termType = tmRdf.getPropertyResourceValue(RML.termType);
        if (termType != null) {
            tm.termType = termType;
        } else if (hasNoTemplateReferenceConstantOrFunction(tmRdf)) {
            tm.termType = RML.BLANKNODE;
        }

        return tm;
    }

    private ObjectMap prepareObjectMap(Resource om) {
        return prepareTermMapFull(om, new ObjectMap());
    }

    private <TM extends TermMap> TM prepareTermMapFull(Resource om, TM termMap) {
        TM objectMap = prepareTermMapMinimal(om, termMap);

        Resource lam = om.getPropertyResourceValue(RML.languageMap);
        if (lam != null) {
            objectMap.languageMap = prepareLanguageMap(lam);
        }

        Resource dtm = om.getPropertyResourceValue(RML.datatypeMap);
        if (dtm != null) {
            objectMap.datatypeMap = prepareDatatypeMap(dtm);
        }

        Resource termType = om.getPropertyResourceValue(RML.termType);
        if (termType == null && (lam != null || dtm != null || objectMap.getExpression() instanceof Reference || objectMap.getExpression() instanceof FunctionExecution)) {
            objectMap.termType = RML.LITERAL;
        }

        Resource gm = om.getPropertyResourceValue(RML.gather);
        if (gm != null) {
            objectMap.gatherMap = prepareGatherMap(om);
        }

        return objectMap;
    }

    private GatherMap prepareGatherMap(Resource gm) {
        GatherMap gatherMap = new GatherMap();

        if (gm.hasProperty(RML.allowEmptyListAndContainer)) {
            gatherMap.allowEmptyListAndContainer = gm.getProperty(RML.allowEmptyListAndContainer).getObject().asLiteral().getBoolean();
        }

        if (gm.hasProperty(RML.gatherAs)) {
            gatherMap.gatherAs = gm.getPropertyResourceValue(RML.gatherAs);
        }

        if (gm.hasProperty(RML.strategy)) {
            gatherMap.strategy = gm.getPropertyResourceValue(RML.strategy);
        }

        RDFList list = gm.getPropertyResourceValue(RML.gather).as(RDFList.class);
        Iterator<RDFNode> iter = list.iterator();
        while (iter.hasNext()) {
            Resource r = iter.next().asResource();

            if (r.hasProperty(RML.parentTriplesMap)) {
                ReferencingObjectMap rom = prepareReferencingObjectMap(r);
                gatherMap.gatherMaps.add(rom);
            } else {
                ObjectMap om = prepareObjectMap(r);
                gatherMap.gatherMaps.add(om);
            }
        }

        return gatherMap;
    }

    private DatatypeMap prepareDatatypeMap(Resource dtm) {
        return prepareExpression(dtm, new DatatypeMap());
    }

    private LanguageMap prepareLanguageMap(Resource lam) {
        return prepareExpression(lam, new LanguageMap());
    }

    private Field prepareField(Resource p) {
        ExpressionAndOrigin exprAndOrigin = prepareExpression(p);
        Field field;
        if (exprAndOrigin.expression == null) {
            IterableField f = new IterableField();

            if (p.hasProperty(RML.iterator)) {
                f.iterator = p.getProperty(RML.iterator).getObject().asLiteral().getString();
            }

            if (p.hasProperty(RML.referenceFormulation)) {
                Statement stmt = p.getProperty(RML.referenceFormulation);
                f.declaredReferenceFormulation = stmt.getObject().asResource();
                f.declaredReferenceFormulationOrigin = new Origin(stmt, Object, null);
            }

            field = f;
        } else {
            ExpressionField f = new ExpressionField();
            ConcreteExpressionMap fem = new ConcreteExpressionMap();
            prepareExpression(p, fem);
            f.fieldExpressionMap = fem;
            field = f;
        }

        field.fieldName = p.getRequiredProperty(RML.fieldName).getObject().asLiteral().getString();

        Field finalField = field;
        p.listProperties(RML.field).forEachRemaining(s -> finalField.addField(prepareField(s.getObject().asResource())));

        return finalField;
    }

    private JoinCondition prepareJoinCondition(Statement joinConditionStmt) {
        JoinCondition jc = new JoinCondition();
        Resource jcr = joinConditionStmt.getObject().asResource();

        Resource r = jcr.getPropertyResourceValue(RML.parentMap);
        if (r != null) {
            jc.parentMap = prepareExpressionMap(r);
        }

        r = jcr.getPropertyResourceValue(RML.childMap);
        if (r != null) {
            jc.childMap = prepareExpressionMap(r);
        }
        return jc;
    }

    private ReferencingObjectMap prepareReferencingObjectMap(Resource rom) {
        ReferencingObjectMap referencingObjectMap = new ReferencingObjectMap();

        Resource p = rom.getPropertyResourceValue(RML.parentTriplesMap);
        referencingObjectMap.parentTriplesMap = triplesMaps.computeIfAbsent(p, TriplesMap::new);

        List<JoinCondition> conditions = new ArrayList<>();
        rom.listProperties(RML.joinCondition).forEachRemaining(s -> conditions.add(prepareJoinCondition(s)));
        referencingObjectMap.joinConditions = conditions;

        rom.listProperties(RML.logicalTarget).forEachRemaining(s -> referencingObjectMap.logicalTargets.add(prepareLogicalTarget(s.getResource())));

        return referencingObjectMap;
    }

    private ConcreteExpressionMap prepareExpressionMap(Resource em) {
        return prepareExpression(em, new ConcreteExpressionMap());
    }

    private <EM extends ExpressionMap> EM prepareExpression(Resource r, EM em) {
        ExpressionAndOrigin exprAndOrigin = prepareExpression(r);
        em.setExpression(exprAndOrigin.expression);
        em.setExpressionOrigin(exprAndOrigin.origin);

        r.listProperties(RML.logicalTarget).forEachRemaining(s -> em.getLogicalTargets().add(prepareLogicalTarget(s.getResource())));

        return em;
    }

    private static class ExpressionAndOrigin {
        Expression expression;
        Origin origin;

        ExpressionAndOrigin(Expression e, Origin o) {
            this.expression = e;
            this.origin = o;
        }
    }

    private ExpressionAndOrigin prepareExpression(Resource r) {
        if (r.hasProperty(RML.constant)) {
            Statement stmt = r.getProperty(RML.constant);
            RDFNode constant = stmt.getObject();
            Term term;
            if (constant.isURIResource()) {
                term = new IRITerm(constant.asResource().getURI());
            } else if (constant.isLiteral()) {
                Literal lit = constant.asLiteral();
                IRITerm dt = lit.getDatatypeURI() != null ? new IRITerm(lit.getDatatypeURI()) : null;
                String lang = (lit.getLanguage() != null && !lit.getLanguage().isEmpty()) ? lit.getLanguage() : null;
                term = new LiteralTerm(lit.getLexicalForm(), dt, lang);
            } else {
                term = new BlankNodeTerm(constant.asResource().getId().getLabelString());
            }
            Origin origin = new Origin(null, List.of(StatementParts.fromPredicateObject(stmt)));
            return new ExpressionAndOrigin(new RDFNodeConstant(term), origin);
        }

        if (r.hasProperty(RML.reference)) {
            String reference = r.getProperty(RML.reference).getObject().asLiteral().getString();
            Origin origin = new Origin(r.getProperty(RML.reference), Object, null);
            return new ExpressionAndOrigin(new RawReference(reference, origin), origin);
        }

        if (r.hasProperty(RML.template)) {
            String template = r.getProperty(RML.template).getObject().asLiteral().getString();
            Origin origin = new Origin(r.getProperty(RML.template), Object, null);
            return new ExpressionAndOrigin(new Template(template, r.getProperty(RML.template)), origin);
        }

        if (r.hasProperty(RML.functionExecution)) {
            FunctionExecution fe = new FunctionExecution();

            Statement feStmt = r.getProperty(RML.functionExecution);
            Resource fer = feStmt.getResource();
            fe.callStmt = StatementParts.from(feStmt, Object);

            fe.functionMap = prepareFunctionMap(fer.getPropertyResourceValue(RML.functionMap));
            fe.functionMapStmt = StatementParts.fromPredicateObject(fer.getProperty(RML.functionMap));

            if (r.hasProperty(RML.returnMap)) {
                fe.returnMap = prepareReturnMap(r.getPropertyResourceValue(RML.returnMap));
                fe.returnMapStmt = StatementParts.fromPredicateObject(r.getProperty(RML.returnMap));
            }

            List<Statement> inputStmts = fer.listProperties(RML.input).toList();
            for (Statement it : inputStmts) {
                fe.inputs.add(prepareInput(it.getResource()));
            }
            fe.inputsStmt = inputStmts.stream().map(it -> StatementParts.from(it, Object)).toList();

            return new ExpressionAndOrigin(fe, new Origin(feStmt, Object, null));
        }

        return new ExpressionAndOrigin(null, null);
    }

    private Input prepareInput(Resource r) {
        Input input = new Input();

        input.parameterMap = prepareExpression(r.getPropertyResourceValue(RML.parameterMap), new ParameterMap());

        input.inputValueMap = prepareInputValueMap(r.getPropertyResourceValue(RML.inputValueMap));

        return input;
    }

    private FunctionMap prepareFunctionMap(Resource r) {
        return prepareExpression(r, new FunctionMap());
    }

    private ReturnMap prepareReturnMap(Resource r) {
        ReturnMap rm = new ReturnMap();
        prepareExpression(r, rm);
        return rm;
    }

    private InputValueMap prepareInputValueMap(Resource om) {
        return prepareTermMapFull(om, new InputValueMap());
    }

    private boolean hasNoTemplateReferenceConstantOrFunction(Resource r) {
        if (r.hasProperty(RML.constant)) return false;
        if (r.hasProperty(RML.reference)) return false;
        if (r.hasProperty(RML.template)) return false;
        return !r.hasProperty(RML.functionExecution);
    }

    public List<StatementParts> extractStatementsFromShaclViolation(ReportEntry vr, Model mapping) {
        List<StatementParts> results = new ArrayList<>();

        Node focusNode = vr.focusNode();
        org.apache.jena.sparql.path.Path resultPath = vr.resultPath();
        Node value = vr.value();

        if (focusNode == null) return results;

        boolean isURI = focusNode.isURI();
        boolean isBlank = focusNode.isBlank();

        if (!isURI && !isBlank) return results;

        Resource focusResource = isURI ? mapping.getResource(focusNode.toString()) : null;

        RDFNode valueNode = null;
        if (value != null && value.isConcrete()) {
            if (value.isURI()) {
                valueNode = mapping.getResource(value.toString());
            } else if (value.isBlank()) {
                valueNode = mapping.getResource(value.toString());
            } else if (value.isLiteral()) {
                valueNode = mapping.createTypedLiteral(value.getLiteralLexicalForm(), value.getLiteralDatatype());
            }
        }

        if (resultPath != null) {
            switch (resultPath) {
                case P_Path0 p0 -> {
                    Property predicate = mapping.getProperty(p0.getNode().getURI());
                    if (valueNode != null) {
                        mapping.listStatements(focusResource, predicate, valueNode).forEachRemaining(stmt -> results.add(StatementParts.from(stmt, Subject, Predicate, Object)));
                    } else {
                        mapping.listStatements(focusResource, predicate, (RDFNode) null).forEachRemaining(stmt -> results.add(StatementParts.from(stmt, Subject, Predicate, Object)));
                    }
                }
                case P_Path1 p1 -> {
                    if (resultPath instanceof P_Inverse pInv) {
                        org.apache.jena.sparql.path.Path subPath = pInv.getSubPath();
                        if (subPath instanceof P_Path0 p0) {
                            Property predicate = mapping.getProperty(p0.getNode().getURI());
                            Resource s = (valueNode != null && valueNode.isResource()) ? valueNode.asResource() : null;
                            mapping.listStatements(s, predicate, focusResource).forEachRemaining(stmt -> results.add(StatementParts.from(stmt, Subject, Predicate, Object)));
                        }
                    }
                }
                case P_Path2 pPath2 -> {
                    // Not handled
                }
                case P_NegPropSet pNegPropSet -> {
                    // Not handled
                }
                default -> {
                }
            }
        }

        if (results.isEmpty()) {
            mapping.listStatements(focusResource, null, valueNode).forEachRemaining(stmt -> results.add(StatementParts.from(stmt, Subject)));

            mapping.listStatements(null, null, focusResource).forEachRemaining(stmt -> {
                boolean contains = results.stream().anyMatch(it -> it.stmt().equals(stmt));
                if (!contains) {
                    results.add(StatementParts.from(stmt, Object));
                }
            });
        }

        return results;
    }

    private LogicalTarget prepareLogicalTarget(Resource r) {
        if (logicalTargets.containsKey(r)) return logicalTargets.get(r);

        Statement targetStmt = r.getProperty(RML.target);
        if (targetStmt == null)
            throw new BurpException(new RmlError("LogicalTarget has no target", null, RER.MappingError, null));

        Resource targetRes = targetStmt.getResource();
        RMLTarget target;
        if (targetRes.hasProperty(RML.path)) {
            String path = targetRes.getProperty(RML.path).getObject().asLiteral().getString();
            Resource root = targetRes.getPropertyResourceValue(RML.root);
            if (root == null) root = RML.CurrentWorkingDirectory;
            target = new FilePathTarget(path, root);
        } else {
            throw new BurpException(new RmlError("Unsupported target type", new Origin(targetStmt, Predicate, Object), RER.MappingError, null));
        }

        Resource serialization = r.getPropertyResourceValue(RML.serialization);
        Resource compression = r.getPropertyResourceValue(RML.compression);
        Resource encoding = r.getPropertyResourceValue(RML.encoding);

        LogicalTarget lt = new LogicalTarget(target, serialization, compression, encoding);
        logicalTargets.put(r, lt);
        return lt;
    }
}
