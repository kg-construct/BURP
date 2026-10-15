package burp.parse.loader;

import burp.model.AbstractPlanNode;
import burp.model.rdf.BlankNodeTerm;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.parse.loader.annotations.*;
import burp.reporting.*;
import burp.vocabularies.RER;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic, high-performance reflection-based RDF-to-Object loader.
 * <p>
 * Converts RDF graph nodes into annotated Java domain objects through metadata caching,
 * polymorphic type resolution, shortcut expansion, container unwrapping, and statement provenance tracking.
 * </p>
 *
 * <h3>Core Architecture &amp; Lifecycle:</h3>
 * <ol>
 *   <li><b>Cache Check:</b> Looks up existing instances for the given {@link Resource} to preserve object identity.</li>
 *   <li><b>Custom Mapper Check:</b> Invokes user-registered custom mappers if present for the target type.</li>
 *   <li><b>Polymorphic Type Resolution:</b> Resolves concrete classes and their defining RDF statements via
 *       {@link TypeDecider}s, explicit {@code rdf:type} bindings, and {@link RdfPolymorphic} annotations.</li>
 *   <li><b>Container Unwrapping ({@link RdfWrapped}):</b> Unwraps intermediate container predicates (e.g. {@code rml:functionExecution})
 *       to construct child instances and link wrapper origins.</li>
 *   <li><b>Class Metadata Caching:</b> Scans class hierarchies once and caches reflection metadata for fields.</li>
 *   <li><b>Field Population &amp; Provenance Tracking:</b>
 *     <ul>
 *       <li>{@link RdfId}: Injects the Jena {@link Resource}.</li>
 *       <li>{@link RdfProperty}: Recursively maps child nodes or literal values and records origins.</li>
 *       <li>{@link RdfShortcutProperty}: Synthesizes subgraphs to expand shortcuts into canonical object trees.</li>
 *       <li>{@link RdfList}: Traverses {@code rdf:List} structures (head/first/rest) into Java collections.</li>
 *       <li>{@link RdfMappedFrom}: In-place mappings from the same RDF resource node.</li>
 *       <li>{@link OriginPredicate}: Injects predicate provenance, {@link AbstractPlanNode#putOrigin(String, Origin)} is used for all the rest.</li>
 *     </ul>
 *   </li>
 *   <li><b>Post-Processing:</b> Runs registered {@link PostProcessor} hooks for contextual initialization.</li>
 * </ol>
 */
public class ReflectiveRdfObjectLoader {

    /**
     * Custom mapper interface for user-defined procedural mapping of specific RDF classes.
     *
     * @param <T> the target object type
     */
    public interface CustomMapper<T> {
        T map(Model model, Resource resource, ReflectiveRdfObjectLoader loader);
    }

    /**
     * Encapsulates the result of a mapping operation along with the defining RDF statement
     * that triggered its type resolution or parent-child edge traversal.
     *
     * @param instance          the mapped domain instance, or {@code null} if unmappable
     * @param definingStatement the RDF statement that triggered or defined the object
     * @param <T>               the type of the mapped instance
     */
    public record MappingResult<T>(@Nullable T instance, @Nullable Statement definingStatement) {
        public static <T> MappingResult<T> of(@Nullable T instance, @Nullable Statement definingStatement) {
            return new MappingResult<>(instance, definingStatement);
        }
    }

    private RdfLoaderContext context = null;
    private final List<TypeDecider> typeDeciders = new ArrayList<>();
    private final Map<Class<?>, CustomMapper<?>> customMappers = new LinkedHashMap<>();
    private final Map<Class<?>, List<PostProcessor<?>>> postProcessors = new LinkedHashMap<>();
    private final Map<String, Class<?>> typeBindings = new LinkedHashMap<>();
    private final Map<Resource, Object> instanceCache = new LinkedHashMap<>();
    private final Map<Class<?>, ClassMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Set<Statement> accessedStatements = Collections.synchronizedSet(new HashSet<>());

    public ReflectiveRdfObjectLoader() {
    }

    public ReflectiveRdfObjectLoader(RdfLoaderContext context) {
        this.context = context;
    }

    public void recordAccessedStatement(@Nullable Statement stmt) {
        if (stmt != null) {
            accessedStatements.add(stmt);
        }
    }

    public void recordAccessedStatements(@Nullable Collection<Statement> stmts) {
        if (stmts != null) {
            for (Statement s : stmts) {
                if (s != null) accessedStatements.add(s);
            }
        }
    }

    public Set<Statement> getAccessedStatements() {
        return accessedStatements;
    }

    public RdfLoaderContext getContext() {
        return context;
    }

    public void setContext(RdfLoaderContext context) {
        this.context = context;
    }

    public void registerTypeDecider(TypeDecider decider) {
        typeDeciders.add(decider);
    }

    public <T> void registerCustomMapper(Class<T> clazz, CustomMapper<T> mapper) {
        customMappers.put(clazz, mapper);
    }

    public <T> void registerPostProcessor(Class<T> clazz, PostProcessor<T> postProcessor) {
        postProcessors.computeIfAbsent(clazz, k -> new ArrayList<>()).add(postProcessor);
    }

    public void registerTypeBinding(String rdfTypeUri, Class<?> clazz) {
        typeBindings.put(rdfTypeUri, clazz);
    }

    public Map<Resource, Object> getCache() {
        return instanceCache;
    }

    public void clearCache() {
        instanceCache.clear();
        accessedStatements.clear();
    }

    /**
     * Maps an RDF resource into an instance of {@code targetClass}.
     *
     * @param model       the Jena RDF model containing graph triples
     * @param resource    the RDF resource to map
     * @param targetClass the target class or interface to instantiate
     * @param <T>         the target object type
     * @return the mapped domain instance, or {@code null} if mapping could not resolve
     */
    public @Nullable <T> T map(Model model, Resource resource, Class<T> targetClass) {
        return mapInternal(model, resource, targetClass, true, null).instance();
    }

    /**
     * Core internal mapping routine. Resolves concrete types, handles container unwrapping,
     * instantiates classes, populates fields, and attaches provenance origins.
     *
     * @param model             the Jena RDF model containing graph triples
     * @param resource          the RDF resource to map
     * @param targetClass       the target class or interface
     * @param allowUnwrap       whether to unwrap intermediate containers (via {@link RdfWrapped})
     * @param incomingStatement the RDF statement via which this node was reached (for origin tracking)
     * @param <T>               the target object type
     * @return a {@link MappingResult} with the mapped instance and defining statement
     */
    @SuppressWarnings("unchecked")
    private <T> MappingResult<T> mapInternal(
            Model model,
            Resource resource,
            Class<T> targetClass,
            boolean allowUnwrap,
            @Nullable Statement incomingStatement
    ) {
        if (resource == null || targetClass == null) {
            return MappingResult.of(null, null);
        }

        if (instanceCache.containsKey(resource)) {
            Object cached = instanceCache.get(resource);
            if (targetClass.isInstance(cached)) {
                return MappingResult.of((T) cached, incomingStatement);
            }
        }

        // Check custom mappers for target class
        if (customMappers.containsKey(targetClass)) {
            T customResult = checkCustomMapper(model, resource, targetClass);
            if (customResult != null) {
                instanceCache.put(resource, customResult);
            }
            return MappingResult.of(customResult, incomingStatement);
        }

        TypeDecider.Decision decision = resolveConcreteClass(model, resource, targetClass);
        Class<?> concreteClass = decision.concreteClass();
        Statement definingStmt = decision.definingStatement() != null ? decision.definingStatement() : incomingStatement;

        // 1. Generic @RdfWrapped container unwrapping
        if (allowUnwrap && concreteClass.isAnnotationPresent(RdfWrapped.class)) {
            RdfWrapped wrappedAnn = concreteClass.getAnnotation(RdfWrapped.class);
            Property wrapProp = ResourceFactory.createProperty(wrappedAnn.value());
            Statement wrapStmt = resource.hasProperty(wrapProp) ? resource.getProperty(wrapProp) : null;
            if (wrapStmt == null) {
                ClassMetadata meta = getClassMetadata(concreteClass);
                for (PropertyMetadata pm : meta.properties()) {
                    if (pm.shortcutPropertyUri() != null) {
                        Property sp = ResourceFactory.createProperty(pm.shortcutPropertyUri());
                        if (resource.hasProperty(sp)) {
                            wrapStmt = resource.getProperty(sp);
                            break;
                        }
                    }
                }
            }
            if (wrapStmt != null) {
                recordAccessedStatement(wrapStmt);
                Resource unwrappedRes = wrapStmt.getObject().isResource() ? wrapStmt.getObject().asResource() : resource;
                MappingResult<T> unwrapped = mapInternal(model, unwrappedRes, (Class) concreteClass, false, wrapStmt);
                if (unwrapped.instance() != null) {
                    T unwrappedInstance = unwrapped.instance();
                    ClassMetadata metadata = getClassMetadata(concreteClass);
                    populateInstance(model, resource, unwrappedInstance, metadata, true);
                    injectWrapperStatement(unwrappedInstance, wrapStmt);
                    return MappingResult.of(unwrappedInstance, wrapStmt);
                }
            }
        }

        if (concreteClass != targetClass) {
            if (customMappers.containsKey(concreteClass)) {
                T customResult = checkCustomMapper(model, resource, concreteClass);
                if (customResult != null) {
                    instanceCache.put(resource, customResult);
                }
                return MappingResult.of(customResult, definingStmt);
            }
        }

        if (concreteClass.isInterface() || Modifier.isAbstract(concreteClass.getModifiers())) {
            return MappingResult.of(null, null);
        }

        try {
            Constructor<?> constructor = concreteClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            T instance = (T) constructor.newInstance();
            instanceCache.put(resource, instance);

            ClassMetadata metadata = getClassMetadata(concreteClass);
            populateInstance(model, resource, instance, metadata, false);

            // Execute registered post-processors for instance class and supertypes
            executePostProcessors(model, resource, instance);

            return MappingResult.of(instance, definingStmt);
        } catch (ReflectiveOperationException e) {
            throw new BurpException(new RmlError("Failed to instantiate and map RDF resource " + resource + " to " + concreteClass.getName(), null, RER.UnexpectedError,e));
        }
    }

    /**
     * Injects provenance origins and statement parts from a container wrapper statement
     * (e.g. {@code [ :child rml:functionExecution <#Exec> ]}) into the unwrapped target instance.
     *
     * @param instance the unwrapped instance (e.g. {@link burp.model.fnml.FunctionExecution})
     * @param wrapStmt the wrapper statement on the outer container
     */
    private void injectWrapperStatement(Object instance, Statement wrapStmt) {
        Origin wrapOrigin = statementOrigin(wrapStmt, StatementPart.Predicate, StatementPart.Object);
        if (instance instanceof AbstractPlanNode planNode) {
            planNode.addExtraOrigin(wrapOrigin);
            if (wrapStmt.getPredicate() != null) {
                planNode.putOrigin(wrapStmt.getPredicate().getLocalName(), wrapOrigin);
            }
        }
        for (Class<?> c = instance.getClass(); c != null; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                if (field.getType().equals(StatementParts.class)) {
                    try {
                        field.setAccessible(true);
                        field.set(instance, StatementParts.from(wrapStmt, StatementPart.Object));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private <T> T checkCustomMapper(Model model, Resource resource, Class<?> clazz) {
        if (clazz != null && customMappers.containsKey(clazz)) {
            CustomMapper<T> custom = (CustomMapper<T>) customMappers.get(clazz);
            return custom.map(model, resource, this);
        }
        return null;
    }

    /**
     * Resolves the concrete implementation class and defining statement for a given RDF resource
     * and target type by checking registered {@link TypeDecider}s, explicit {@code rdf:type} bindings,
     * and polymorphic annotations ({@link RdfPolymorphic}).
     *
     * @param model       the Jena RDF model
     * @param resource    the RDF resource
     * @param targetClass the interface or base class to resolve
     * @return a {@link TypeDecider.Decision} containing the concrete class and defining statement
     */
    private TypeDecider.Decision resolveConcreteClass(Model model, Resource resource, Class<?> targetClass) {
        for (TypeDecider decider : typeDeciders) {
            TypeDecider.Decision decision = decider.decideType(model, resource, targetClass);
            if (decision != null && targetClass.isAssignableFrom(decision.concreteClass())) {
                if (decision.definingStatement() != null) {
                    recordAccessedStatement(decision.definingStatement());
                }
                return decision;
            }
        }

        StmtIterator typeStmts = resource.listProperties(RDF.type);
        while (typeStmts.hasNext()) {
            Statement stmt = typeStmts.nextStatement();
            if (stmt.getObject().isURIResource()) {
                String typeUri = stmt.getObject().asResource().getURI();
                if (typeBindings.containsKey(typeUri)) {
                    Class<?> bound = typeBindings.get(typeUri);
                    if (targetClass.isAssignableFrom(bound)) {
                        recordAccessedStatement(stmt);
                        return new TypeDecider.Decision(bound, stmt);
                    }
                }
            }
        }

        if (targetClass.isAnnotationPresent(RdfPolymorphic.class)) {
            RdfPolymorphic poly = targetClass.getAnnotation(RdfPolymorphic.class);
            for (Class<?> candidate : poly.classes()) {
                if (candidate.isAnnotationPresent(RdfType.class)) {
                    String typeUri = candidate.getAnnotation(RdfType.class).value();
                    for (Statement stmt : resource.listProperties(RDF.type).toList()) {
                        if (stmt.getObject().isResource() && stmt.getObject().asResource().getURI() != null) {
                            if (stmt.getObject().asResource().getURI().equalsIgnoreCase(typeUri)) {
                                if (targetClass.isAssignableFrom(candidate)) {
                                    recordAccessedStatement(stmt);
                                    return new TypeDecider.Decision(candidate, stmt);
                                }
                            }
                        }
                    }
                }
            }
        }

        return new TypeDecider.Decision(targetClass, null);
    }

    private boolean isSynthetic(@Nullable Resource resource) {
        return resource != null && resource.isURIResource() && resource.getURI().startsWith("urn:burp:synth:");
    }

    /**
     * Populates fields on the instantiated object using metadata extracted from annotations:
     * {@link RdfId}, {@link RdfProperty}, {@link RdfShortcutProperty}, {@link RdfList},
     * {@link RdfMappedFrom}, and {@link OriginPredicate}.
     *
     * @param model      the Jena RDF model
     * @param resource   the RDF resource node
     * @param instance   the newly instantiated object
     * @param metadata   the cached reflection metadata for {@code instance.getClass()}
     * @param isWrapper  whether this instance is being populated as an unwrapped wrapper target
     */
    private void populateInstance(Model model, Resource resource, Object instance, ClassMetadata metadata, boolean isWrapper) {
        Map<String, Statement> propertyStatements = new HashMap<>();
        boolean synthetic = isSynthetic(resource);
        if (!isWrapper && !synthetic) {
            if (metadata.rdfTypeUri() != null) {
                Resource typeRes = ResourceFactory.createResource(metadata.rdfTypeUri());
                for (Statement typeStmt : resource.listProperties(RDF.type).toList()) {
                    if (typeStmt.getObject().equals(typeRes)) {
                        recordAccessedStatement(typeStmt);
                        if (instance instanceof AbstractPlanNode planNode) {
                            planNode.addExtraOrigin(statementOrigin(typeStmt, StatementPart.Subject, StatementPart.Predicate, StatementPart.Object));
                        }
                    }
                }
            }
        }

        // 1. Inject @RdfId
        if (!isWrapper && metadata.idField() != null) {
            if (!metadata.idField().getType().equals(Resource.class))
                throw new RuntimeException("Field " + metadata.idField() + " annotated with @RdfId must be of type org.apache.jena.rdf.model.Resource.");
            setFieldValue(instance, metadata.idField(), resource);
        }

        // 2. Inject @RdfProperty and @RdfShortcutProperty
        for (PropertyMetadata pm : metadata.properties()) {
            Property rdfProp = ResourceFactory.createProperty(pm.propertyUri());
            List<Statement> stmts = resource.listProperties(rdfProp).toList();

            if (!stmts.isEmpty()) {
                if (!synthetic) {
                    recordAccessedStatements(stmts);
                    propertyStatements.put(pm.field().getName(), stmts.getFirst());
                    if (instance instanceof AbstractPlanNode planNode) {
                        for (Statement stmt : stmts) {
                            Origin o = statementOrigin(stmt, StatementPart.Predicate, StatementPart.Object);
                            planNode.putOrigin(pm.field().getName(), o);
                        }
                    }
                }
                Class<?> targetType = Collection.class.isAssignableFrom(pm.field().getType()) ? pm.genericType() : pm.field().getType();
                List<Object> mappedValues = new ArrayList<>(stmts.size());
                for (Statement stmt : stmts) {
                    Object mappedValue = mapNodeValue(model, stmt.getObject(), targetType, stmt);
                    if (!synthetic) {
                        injectOriginPredicateIfPresent(mappedValue, stmt);
                    }
                    mappedValues.add(mappedValue);
                }
                assignFieldValue(instance, pm.field(), mappedValues, isWrapper);
            } else if (pm.shortcutPropertyUri() != null) {
                Property shortcutProp = ResourceFactory.createProperty(pm.shortcutPropertyUri());
                List<Statement> shortcutStmts = resource.listProperties(shortcutProp).toList();
                if (!shortcutStmts.isEmpty()) {
                    if (!synthetic) {
                        recordAccessedStatements(shortcutStmts);
                        propertyStatements.put(pm.field().getName(), shortcutStmts.getFirst());
                        if (instance instanceof AbstractPlanNode planNode) {
                            for (Statement sStmt : shortcutStmts) {
                                Origin o = statementOrigin(sStmt, StatementPart.Predicate, StatementPart.Object);
                                planNode.putOrigin(pm.field().getName(), o);
                            }
                        }
                    }
                    Class<?> targetType = Collection.class.isAssignableFrom(pm.field().getType()) ? pm.genericType() : pm.field().getType();
                    List<Object> mappedValues = new ArrayList<>(shortcutStmts.size());
                    for (Statement sStmt : shortcutStmts) {
                        Object synthMapped = mapSyntheticShortcut(model, sStmt.getObject(), targetType, pm.targetPropertyUri(), sStmt);
                        if (!synthetic) {
                            injectOriginPredicateIfPresent(synthMapped, sStmt);
                        }
                        mappedValues.add(synthMapped);
                    }
                    assignFieldValue(instance, pm.field(), mappedValues, isWrapper);
                }
            }
        }

        // 3. Inject @RdfList
        for (ListPropertyMetadata lpm : metadata.lists()) {
            Property prop = ResourceFactory.createProperty(lpm.propertyUri());
            List<Statement> listStmts = resource.listProperties(prop).toList();
            if (!listStmts.isEmpty()) {
                if (!synthetic) {
                    recordAccessedStatements(listStmts);
                    Statement headStmt = listStmts.getFirst();
                    propertyStatements.put(lpm.field().getName(), headStmt);
                    if (instance instanceof AbstractPlanNode planNode) {
                        Origin o = statementOrigin(headStmt, StatementPart.Predicate, StatementPart.Object);
                        planNode.putOrigin(lpm.field().getName(), o);
                    }
                }
            }
            Resource listHead = resource.getPropertyResourceValue(prop);
            if (listHead != null && listHead.canAs(RDFList.class)) {
                RDFList list = listHead.as(RDFList.class);
                List<Object> mappedItems = new ArrayList<>();
                RDFList currentList = list;
                while (!currentList.isEmpty()) {
                    RDFNode item = currentList.getHead();
                    Statement firstStmt = currentList.getProperty(RDF.first);
                    if (firstStmt != null && !synthetic) {
                        recordAccessedStatement(firstStmt);
                    }
                    Object mappedItem = mapNodeValue(model, item, lpm.genericType(), firstStmt);
                    if (mappedItem instanceof AbstractPlanNode childNode && firstStmt != null && !synthetic) {
                        childNode.addExtraOrigin(statementOrigin(firstStmt, StatementPart.Object));
                    }
                    mappedItems.add(mappedItem);
                    currentList = currentList.getTail();
                }
                assignFieldValue(instance, lpm.field(), mappedItems, isWrapper);
            }
        }

        // 4. Inject @RdfMappedFrom
        for (MappedFromMetadata mfm : metadata.mappedFrom()) {
            MappingResult<?> mappedResult = null;
            if (mfm.candidateClasses().length > 0) {
                for (Class<?> candidate : mfm.candidateClasses()) {
                    mappedResult = mapInternal(model, resource, candidate, true, null);
                    if (mappedResult.instance() != null) {
                        break;
                    }
                }
            } else {
                mappedResult = mapInternal(model, resource, mfm.field().getType(), true, null);
            }
            if (mappedResult.instance() != null) {
                Object mapped = mappedResult.instance();
                setFieldValue(instance, mfm.field(), mapped);
                Statement matchedStmt = mappedResult.definingStatement();
                if (matchedStmt != null && !synthetic) {
                    recordAccessedStatement(matchedStmt);
                    propertyStatements.put(mfm.field().getName(), matchedStmt);
                    if (instance instanceof AbstractPlanNode planNode) {
                        Origin o = statementOrigin(matchedStmt, StatementPart.Predicate, StatementPart.Object);
                        planNode.putOrigin(mfm.field().getName(), o);
                    }
                    if (mapped instanceof AbstractPlanNode childNode) {
                        childNode.addExtraOrigin(statementOrigin(matchedStmt, StatementPart.Predicate, StatementPart.Object));
                    }
                }
            }
        }

        if (!synthetic) {
            // 5. Inject @OriginOf
            for (OriginMetadata om : metadata.origins()) {
                Statement stmt = propertyStatements.get(om.targetPropertyName());
                if (stmt != null) {
                    assignOriginField(instance, om.field(), stmt, om.part());
                }
            }

            // 6. Inject @OriginPredicate
            for (OriginPredicateMetadata opm : metadata.originPredicates()) {
                Statement stmt = null;
                if (opm.targetPropertyName() != null && !opm.targetPropertyName().isEmpty()) {
                    stmt = propertyStatements.get(opm.targetPropertyName());
                } else if (!propertyStatements.isEmpty()) {
                    stmt = propertyStatements.values().iterator().next();
                }
                if (stmt != null) {
                    assignOriginField(instance, opm.field(), stmt, StatementPart.Predicate);
                }
            }
        }
    }

    /**
     * Sets an {@link Origin} or {@link StatementParts} value on a target field.
     *
     * @param target the target object holding the field
     * @param field  the target field (must be of type {@link Origin} or {@link RDFPointer})
     * @param stmt   the RDF statement to extract provenance from
     * @param part   the statement part to track
     */
    private void assignOriginField(Object target, Field field, Statement stmt, StatementPart part) {
        if (field.getType().equals(Origin.class)) {
            setFieldValue(target, field, new Origin(stmt, part));
        } else if (RDFPointer.class.isAssignableFrom(field.getType())) {
            setFieldValue(target, field, StatementParts.from(stmt, part));
        }
    }

    /**
     * Injects predicate origins on fields annotated with {@link OriginPredicate} in the target object.
     *
     * @param target the target object
     * @param stmt   the accessing RDF statement
     */
    private void injectOriginPredicateIfPresent(@Nullable Object target, @Nullable Statement stmt) {
        if (target == null || stmt == null) return;
        ClassMetadata meta = getClassMetadata(target.getClass());
        for (OriginPredicateMetadata opm : meta.originPredicates()) {
            assignOriginField(target, opm.field(), stmt, StatementPart.Predicate);
        }
    }

    /**
     * Executes all registered {@link PostProcessor} hooks for the given instance and its supertypes.
     *
     * @param model    the Jena RDF model
     * @param resource the RDF resource node
     * @param instance the populated domain instance
     * @param <T>      the instance type
     */
    private <T> void executePostProcessors(Model model, Resource resource, T instance) {
        for (Map.Entry<Class<?>, List<PostProcessor<?>>> entry : postProcessors.entrySet()) {
            if (entry.getKey().isInstance(instance)) {
                for (PostProcessor<?> processor : entry.getValue()) {
                    ((PostProcessor<T>) processor).process(model, resource, instance, this);
                }
            }
        }
    }

    /**
     * Expands an RDF shortcut property by creating a synthetic intermediate RDF resource node
     * with canonical properties, mapping it into the target domain type, and linking shortcut provenance.
     *
     * @param model             the underlying RDF model
     * @param shortcutValue     the RDF node value from the shortcut triple
     * @param targetType        the desired target class of the synthetic node
     * @param targetPropertyUri the canonical property URI to assign on the synthetic resource
     * @param shortcutStmt      the source shortcut statement in the original mapping
     * @return the mapped domain object with attached shortcut origins
     */
    private Object mapSyntheticShortcut(Model model, RDFNode shortcutValue, Class<?> targetType, String targetPropertyUri, Statement shortcutStmt) {
        Model synthModel = ModelFactory.createDefaultModel();
        Resource synthRes = synthModel.createResource("urn:burp:synth:" + UUID.randomUUID());
        Property targetProp = synthModel.createProperty(targetPropertyUri);
        synthRes.addProperty(targetProp, shortcutValue);

        Model combinedModel = ModelFactory.createUnion(model, synthModel);
        MappingResult<?> mappedResult = mapInternal(combinedModel, synthRes, targetType, true, shortcutStmt);
        // MappedInternal already attached provenance but did not know how to attach shortcut origin
        Object mapped = mappedResult.instance();
        if (mapped != null) {
            Origin shortcutOrigin = statementOrigin(shortcutStmt, StatementPart.Predicate, StatementPart.Object);
            Origin objOrigin = statementOrigin(shortcutStmt, StatementPart.Object);

            if (mapped instanceof AbstractPlanNode mappedNode) {
                mappedNode.addExtraOrigin(shortcutOrigin);
                mappedNode.putOrigin(targetProp.getLocalName(), objOrigin);
            }

            ClassMetadata meta = getClassMetadata(mapped.getClass());
            for (OriginMetadata om : meta.origins()) {
                assignOriginField(mapped, om.field(), shortcutStmt, om.part());
            }
            for (OriginPredicateMetadata opm : meta.originPredicates()) {
                assignOriginField(mapped, opm.field(), shortcutStmt, StatementPart.Predicate);
            }

            for (MappedFromMetadata mfm : meta.mappedFrom()) {
                if (mapped instanceof AbstractPlanNode mappedNode) {
                    mappedNode.putOrigin(mfm.field().getName(), shortcutOrigin);
                }
                Object child = getFieldValue(mapped, mfm.field());
                if (child != null) {
                    if (child instanceof AbstractPlanNode childNode) {
                        childNode.putOrigin(targetProp.getLocalName(), objOrigin);
                        childNode.addExtraOrigin(shortcutOrigin);
                    }

                    ClassMetadata childMeta = getClassMetadata(child.getClass());
                    for (OriginMetadata om : childMeta.origins()) {
                        assignOriginField(child, om.field(), shortcutStmt, om.part());
                    }
                    for (OriginPredicateMetadata opm : childMeta.originPredicates()) {
                        assignOriginField(child, opm.field(), shortcutStmt, StatementPart.Predicate);
                    }
                }
            }
        }
        return mapped;
    }

    /**
     * Assigns mapped values to single or collection fields.
     *
     * @param instance  the target object holding the field
     * @param field     the field to populate
     * @param values    the list of mapped values
     * @param isWrapper whether this is an unwrapped wrapper pass (merging into existing collections)
     */
    @SuppressWarnings("unchecked")
    private void assignFieldValue(Object instance, Field field, List<Object> values, boolean isWrapper) {
        if (values.isEmpty()) return;
        if (Collection.class.isAssignableFrom(field.getType())) {
            Collection<Object> collection = isWrapper ? (Collection<Object>) getFieldValue(instance, field) : null;
            if (collection == null) {
                collection = createCollection(field.getType());
                setFieldValue(instance, field, collection);
            }
            for (Object val : values) {
                if (val != null) {
                    collection.add(val);
                }
            }
        } else {
            setFieldValue(instance, field, values.getFirst());
        }
    }

    private Object mapNodeValue(Model model, RDFNode node, Class<?> targetType) {
        return mapNodeValue(model, node, targetType, null);
    }

    /**
     * Maps an individual RDFNode value to primitive types, String, Jena Resource, Term, or domain sub-objects.
     *
     * @param model      the Jena RDF model
     * @param node       the RDF node to convert
     * @param targetType the target Java type
     * @param stmt       the incoming RDF statement (for provenance tracking)
     * @return the mapped Java value
     */
    private Object mapNodeValue(Model model, RDFNode node, Class<?> targetType, @Nullable Statement stmt) {
        if (targetType.equals(String.class)) {
            if (node.isLiteral()) return node.asLiteral().getLexicalForm();
            if (node.isURIResource()) return node.asResource().getURI();
            return node.toString();
        }
        if (targetType.equals(Resource.class)) {
            return node.isResource() ? node.asResource() : null;
        }
        if (targetType.equals(RDFNode.class)) {
            return node;
        }
        if (targetType.equals(Integer.class) || targetType.equals(int.class)) {
            return node.isLiteral() ? node.asLiteral().getInt() : 0;
        }
        if (targetType.equals(Long.class) || targetType.equals(long.class)) {
            return node.isLiteral() ? node.asLiteral().getLong() : 0L;
        }
        if (targetType.equals(Boolean.class) || targetType.equals(boolean.class)) {
            return node.isLiteral() && node.asLiteral().getBoolean();
        }
        if (targetType.equals(Double.class) || targetType.equals(double.class)) {
            return node.isLiteral() ? node.asLiteral().getDouble() : 0.0;
        }
        if (targetType.equals(Term.class)) {
            return toRmlTerm(node);
        }

        if (node.isResource()) {
            MappingResult<?> childResult = mapInternal(model, node.asResource(), targetType, true, stmt);
            Object child = childResult.instance();
            if (child instanceof AbstractPlanNode childNode && stmt != null) {
                childNode.addExtraOrigin(statementOrigin(stmt, StatementPart.Predicate, StatementPart.Object));
            }
            return child;
        }

        return null;
    }

    /**
     * Converts a Jena {@link RDFNode} into an internal RML {@link Term} representation
     * (e.g. {@link IRITerm}, {@link LiteralTerm}, or {@link BlankNodeTerm}).
     *
     * @param node the Jena RDF node to convert
     * @return the corresponding {@link Term}, or {@code null} if input is null
     */
    public static Term toRmlTerm(RDFNode node) {
        if (node == null) return null;
        if (node.isURIResource()) {
            return new IRITerm(node.asResource().getURI());
        }
        if (node.isLiteral()) {
            Literal lit = node.asLiteral();
            IRITerm dt = lit.getDatatypeURI() != null ? new IRITerm(lit.getDatatypeURI()) : null;
            String lang = (lit.getLanguage() != null && !lit.getLanguage().isEmpty()) ? lit.getLanguage() : null;
            return new LiteralTerm(lit.getLexicalForm(), dt, lang);
        }
        if (node.isAnon()) {
            return new BlankNodeTerm(node.asResource().getId().getLabelString());
        }
        return null;
    }

    private Collection<Object> createCollection(Class<?> collType) {
        if (Set.class.isAssignableFrom(collType)) {
            return new LinkedHashSet<>();
        }
        return new ArrayList<>();
    }

    private void setFieldValue(Object instance, Field field, Object value) {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set field " + field.getName() + " on " + instance.getClass().getName(), e);
        }
    }

    private Object getFieldValue(Object instance, Field field) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to get field " + field.getName() + " on " + instance.getClass().getName(), e);
        }
    }

    /**
     * Retrieves cached reflection metadata for a domain class, scanning its hierarchy on first access.
     *
     * @param clazz the class to inspect
     * @return the cached {@link ClassMetadata}
     */
    private ClassMetadata getClassMetadata(Class<?> clazz) {
        return metadataCache.computeIfAbsent(clazz, this::scanClassMetadata);
    }

    /**
     * Reflectively scans a class and its superclasses for RDF loader annotations
     * ({@link RdfId}, {@link RdfProperty}, {@link RdfShortcutProperty}, {@link RdfList},
     * {@link RdfMappedFrom}, {@link OriginPredicate}, {@link RdfType}).
     *
     * @param clazz the class to scan
     * @return the compiled {@link ClassMetadata}
     */
    private ClassMetadata scanClassMetadata(Class<?> clazz) {
        Field idField = null;
        List<PropertyMetadata> properties = new ArrayList<>();
        List<ListPropertyMetadata> lists = new ArrayList<>();
        List<MappedFromMetadata> mappedFrom = new ArrayList<>();
        List<OriginMetadata> origins = new ArrayList<>();
        List<OriginPredicateMetadata> originPredicates = new ArrayList<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(RdfId.class)) {
                    idField = field;
                }
                if (field.isAnnotationPresent(RdfProperty.class)) {
                    RdfProperty ann = field.getAnnotation(RdfProperty.class);
                    RdfShortcutProperty shortcut = field.getAnnotation(RdfShortcutProperty.class);
                    Class<?> genericType = getGenericType(field);
                    properties.add(new PropertyMetadata(
                            field,
                            ann.value(),
                            ann.required(),
                            shortcut != null ? shortcut.shortcutUri() : null,
                            shortcut != null ? shortcut.targetProperty() : null,
                            genericType
                    ));
                }
                if (field.isAnnotationPresent(RdfList.class)) {
                    RdfList ann = field.getAnnotation(RdfList.class);
                    Class<?> genericType = getGenericType(field);
                    lists.add(new ListPropertyMetadata(field, ann.value(), genericType));
                }
                if (field.isAnnotationPresent(RdfMappedFrom.class)) {
                    RdfMappedFrom ann = field.getAnnotation(RdfMappedFrom.class);
                    mappedFrom.add(new MappedFromMetadata(field, ann.value()));
                }
                if (field.isAnnotationPresent(OriginPredicate.class)) {
                    OriginPredicate ann = field.getAnnotation(OriginPredicate.class);
                    originPredicates.add(new OriginPredicateMetadata(field, ann.propertyName()));
                }
            }
            current = current.getSuperclass();
        }
        String rdfTypeUri = null;
        if (clazz.isAnnotationPresent(RdfType.class)) {
            rdfTypeUri = clazz.getAnnotation(RdfType.class).value();
        }
        return new ClassMetadata(idField, properties, lists, mappedFrom, origins, originPredicates, rdfTypeUri);
    }

    /**
     * Resolves the source file path associated with an RDF resource by inspecting its statements.
     *
     * @param model    the Jena RDF model
     * @param resource the RDF resource
     * @return the source {@link Path}, or {@code null} if unknown
     */
    public @Nullable Path resolveResourceFile(Model model, Resource resource) {
        if (resource == null) return null;
        StmtIterator it = resource.listProperties();
        while (it.hasNext()) {
            Statement s = it.nextStatement();
            if (s.getModel() != null) {
                var info = burp.parse.turtleprov.RDF12Converter.fromAnnotations(s, s.getModel());
                Path p = info.sourcePath();
                if (p != null) {
                    return p;
                }
            }
        }
        StmtIterator inIt = model.listStatements(null, null, resource);
        while (inIt.hasNext()) {
            Statement s = inIt.nextStatement();
            if (s.getModel() != null) {
                var info = burp.parse.turtleprov.RDF12Converter.fromAnnotations(s, s.getModel());
                Path p = info.sourcePath();
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private Origin statementOrigin(Statement stmt, StatementPart... parts) {
        return new Origin(stmt, parts);
    }

    private Class<?> getGenericType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        return field.getType();
    }

    private record ClassMetadata(
            Field idField,
            List<PropertyMetadata> properties,
            List<ListPropertyMetadata> lists,
            List<MappedFromMetadata> mappedFrom,
            List<OriginMetadata> origins,
            List<OriginPredicateMetadata> originPredicates,
            @Nullable String rdfTypeUri) {
    }

    private record OriginPredicateMetadata(Field field, String targetPropertyName) {
    }

    private record OriginMetadata(Field field, String targetPropertyName, StatementPart part) {
    }

    private record PropertyMetadata(
            Field field,
            String propertyUri,
            boolean required,
            String shortcutPropertyUri,
            String targetPropertyUri,
            Class<?> genericType) {
    }

    private record ListPropertyMetadata(Field field, String propertyUri, Class<?> genericType) {
    }

    private record MappedFromMetadata(Field field, Class<?>[] candidateClasses) {
    }
}
