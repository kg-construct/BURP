/* tslint:disable */
/* eslint-disable */

export interface MappingWorkspace extends WorkspaceService {
    openDocuments: { [index: string]: string };
    documentPrefixes: { [index: string]: { [index: string]: string } };
    currentWorkingDirectory: Path;
    baseIri?: string;
    inlineHintsEvaluationEnabled: boolean;
    iterationSampleLimit?: number;
    client?: LanguageClient;
    cache: DevServerCache;
    referenceCompleter: ReferenceCompleter;
    inlayHintProvider: LspInlineHintProvider;
    codeLensProvider: LspCodeLensProvider;
    evaluator: RmlEvaluator;
    textDocumentService: TextDocumentService;
}

export interface InlineHintsSettings {
    enableEvaluation?: boolean;
}

export interface LoadedMapping {
    document: MappingDocument;
    loader: RmlLoader;
    model?: Model;
    report: RmlExecutionReport;
}

export interface ResolvedNode {
    targetNode: PlanNode;
    mappingDocument: MappingDocument;
}

export interface RmlSettings {
    baseIRI?: string;
    inlineHints?: InlineHintsSettings;
    iterationSampleLimit?: number;
}

export interface Settings {
    rml?: RmlSettings;
}

export interface RmlEvaluationResultFormatter {
}

export interface DevServerCache {
    sourceIterationCache: Cache<AstKey, Iteration[]>;
    customIterationCache: Cache<CustomSourceCacheKey, Iteration[]>;
    parentJoinCache: Cache<ParentJoinCacheKey, ParentJoinIndex>;
    nodeMemoCache: Cache<NodeMemoCacheKey, RmlEvaluationResult[]>;
    sqlSchemaCache: Cache<string, CachedSchema>;
}

export interface CustomSourceCacheKey {
    sourceKey: AstKey;
    customContext: CustomDataContext;
}

export interface NodeMemoCacheKey {
    nodeKey: AstKey;
    iterationKey: number;
}

export interface ParentJoinCacheKey {
    nodeKey: AstKey;
    parentSourceKey: AstKey;
    dataContext?: EvaluationDataContext;
}

export interface ReferenceCompleter {
}

export interface ReferenceSchemaResolver {
}

export interface SchemaInfo {
    referenceFormulation: string;
    candidateValues: { [index: string]: string };
    candidateOrigins: { [index: string]: ReferenceCompletionOrigin };
    hasKnownSchemaOrData: boolean;
}

export interface AllDataContext extends EvaluationDataContext {
}

export interface CustomDataContext extends EvaluationDataContext {
}

export interface EvaluateParams {
    structure?: StructureSelector;
    dataContext: EvaluationDataContext;
}

export interface EvaluateResult {
    iterationId: string;
    iterationData: string;
    generatedRdf: RmlEvaluationResult[];
    diagnostics: string[];
    rerDiagnostic?: string;
    prefixMap: { [index: string]: string };
}

export interface EvaluationContexts {
}

export interface NodeEvaluationScope {
    iteration: Iteration;
    context: EvaluationContext;
}

export interface EvaluationDataContext {
}

export interface InlineEvaluateItem {
    id: string;
    type: string;
    originRange: Range;
    values: RmlEvaluationResult[];
    formattedValues: string[];
    rawFormattedValues: string[];
    diagnostics: string[];
}

export interface InlineEvaluateParams {
    mappingFileUri: string;
    dataContext: EvaluationDataContext;
    range?: Range;
}

export interface InlineEvaluator {
}

export interface MultiSourceDataContext extends EvaluationDataContext {
    sources: { [index: string]: EvaluationDataContext };
    defaultContext?: EvaluationDataContext;
}

export interface RangeDataContext extends EvaluationDataContext {
    startIndex: number;
    endIndex: number;
    specificIndices?: number[];
}

export interface RawStringCustomDataContext extends CustomDataContext {
    rawString: string;
}

export interface ReferenceValuesCustomDataContext extends CustomDataContext {
    referenceValues: { [index: string]: any };
}

export interface RmlEvaluator {
    cache?: DevServerCache;
}

export interface ExecuteParams {
    structure?: StructureSelector;
    executionId?: string;
    baseIri?: string;
}

export interface ExecuteResult {
    targets: { [index: string]: TargetExecutionResult };
    reportUpdate: string;
    prefixMap: { [index: string]: string };
    logicalSources: { [index: string]: string };
}

export interface LogicalTargetInfo {
    targetType?: string;
    targetPath?: string;
    serialization?: string;
    compression?: string;
    encoding?: string;
}

export interface QuadChunkNotification {
    executionId: string;
    targetId: string;
    statements: RdfStatement[];
    logicalSources: { [index: string]: string };
    chunkIndex: number;
    isLast: boolean;
}

export interface RmlExecutionService {
}

export interface TargetExecutionResult {
    targetId: string;
    logicalTarget?: LogicalTargetInfo;
    statements: RdfStatement[];
    statementCount: number;
}

export interface JoinSourceHintProvider {
}

export interface LspInlineHintProvider {
    inlineEvaluator: InlineEvaluator;
    joinSourceHintProvider: JoinSourceHintProvider;
    parameterHintProvider: ParameterHintProvider;
}

export interface ParameterHintProvider {
}

export interface PlanAttributeItem {
    key: string;
    value: string;
    originRange?: Range;
}

export interface PlanInspector {
}

export interface PlanNodeDescriptor {
}

export interface PlanNodeItem {
    id: string;
    type: string;
    originRange: Range[];
    resource?: string;
    displayResource?: string;
    attributes: PlanAttributeItem[];
    references: PlanReferenceItem[];
    children: PlanNodeItem[];
}

export interface PlanParams {
    mappingFileUri: string;
    structure?: StructureSelector;
}

export interface PlanReferenceItem {
    role: string;
    targetId: string;
    targetLabel: string;
    targetResource?: string;
    originRange?: Range;
}

export interface PlanResult {
    roots: PlanNodeItem[];
    selectedNodeId?: string;
}

export interface ReferenceDependency {
    referenceFormulationType: string;
    referenceId: string;
    placeholders: string[];
}

export interface ReferencesDependenciesParams {
    structure: StructureSelector;
}

export interface CsvSchemaProvider extends SchemaProvider {
}

export interface JsonSampledSchemaProvider extends SchemaProvider {
    iterationSampleLimit?: number;
}

export interface JsonSchemaProvider extends SchemaProvider {
}

export interface LogicalViewSchemaProvider extends SchemaProvider {
}

export interface ReferenceCompletionOrigin {
    type: string;
    resource?: Resource;
    location?: string;
    graphPointer?: string;
}

export interface SchemaProvider {
}

export interface SqlSchemaProvider extends SchemaProvider {
    devServerCache?: DevServerCache;
    localCache: { [index: string]: CachedSchema };
}

export interface CachedSchema {
    schemaInfo: SchemaInfo;
    dialectRules: SqlDialectRules;
    error?: SqlSourceError;
    timestamp: number;
}

export interface SqlDialectRules {
    storesUpperCase: boolean;
    storesLowerCase: boolean;
    storesMixedCase: boolean;
    quoteString: string;
}

export interface SqlSourceError {
    kind: ErrorKind;
    message: string;
    cause?: Exception;
}

export interface CursorSelector extends StructureSelector {
    mappingFileUri: string;
    position: Position;
}

export interface DocumentSelector extends StructureSelector {
    textDocument: TextDocumentIdentifier;
}

export interface IdSelector extends StructureSelector {
    mappingFileUri: string;
    id: string;
}

export interface IriSelector extends StructureSelector {
    mappingFileUri: string;
    iri: string;
}

export interface StructureSelector {
}

export interface DocumentData {
    language: string;
    items: string[];
}

export interface LspCodeLensProvider {
}

export interface SourceDataInspector {
}

export interface SourceDataParams {
    structure: StructureSelector;
    offset: number;
    limit: number;
    dataContext?: EvaluationDataContext;
}

export interface SourceDataResult {
    structureId: string;
    structureResource?: string;
    sourceType: string;
    format: string;
    table?: TableData;
    document?: DocumentData;
    offset: number;
    limit: number;
    totalItems: number;
    totalExact: boolean;
    hasMore: boolean;
    diagnostics: string[];
}

export interface TableData {
    headers: string[];
    columnTypes: { [index: string]: string };
    cellTypes: string[][];
    rows: string[][];
}

export interface PlanNodeTopologicalSorter {
}

export interface RerProvSerializer {
}

export interface RmlTracer {
    engine: TracePipelineEngine;
}

export interface TraceExecutor {
}

export interface TraceGraph {
    nodes: { [index: string]: Node };
}

export interface Node {
    planNode: PlanNode;
    inputs: Value[];
    outputs: Value[];
    upstream: Node[];
    downstream: Node[];
}

export interface Value {
    producer: PlanNode;
    iteration?: Iteration;
    result?: RmlEvaluationResult;
    text: string;
    derivedFrom: Value[];
}

export interface TraceGraphExporter {
}

export interface TraceIterationParams {
    structure: StructureSelector;
    iterationIndex: number;
}

export interface TraceNode {
    nodeId: string;
    nodeType: string;
    label?: string;
    originRange?: Range;
    attributes: PlanAttributeItem[];
    inputs: TraceValue[];
    outputs: TraceValue[];
    dependencyIds: string[];
    futureIds: string[];
}

export interface TraceParams {
    structure: StructureSelector;
    dataContext: EvaluationDataContext;
}

export interface TracePipelineEngine {
}

export interface TraceResult {
    nodes: TraceNode[];
    graphJsonLD: string;
    prefixMap: { [index: string]: string };
}

export interface TraceResultParams {
    structure: StructureSelector;
    subject: string;
    predicate: string;
    object: string;
    graph?: string;
    lineage?: OriginLineage;
}

export interface TraceSlicer {
}

export interface TraceValue {
    valueId: string;
    iterationId: string;
    text: string;
    term?: RmlEvaluationResult;
    producerNodeId: string;
    derivedFromValueIds: string[];
}

export interface FunctionExecutionValidator {
}

export interface ReferenceValidator {
}

export interface RmlDiagnostic {
    range?: Range;
    severity: DiagnosticSeverity;
    rerCodeUri?: string;
    message: string;
    source?: string;
}

export interface StaticMappingValidator {
    referenceValidator: ReferenceValidator;
    functionExecutionValidator: FunctionExecutionValidator;
    unusedElementValidator: UnusedElementValidator;
}

export interface UnusedElementValidator {
}

export interface ValidateParams {
    mappingFileUri: string;
}

export interface RawValue extends RmlEvaluationResult {
    value?: any;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface RdfPredicateObject extends RmlEvaluationResult {
    predicate: IRITerm;
    object: Term;
    graph?: IRITerm;
    targets: LogicalTarget[];
}

export interface RdfStatement extends RdfStatementLike {
    subject: BlankNodeOrIRI;
    predicate: IRITerm;
    object: Term;
    graph?: IRITerm;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface RdfStatementSubjectGraph extends RdfStatementLike {
    subject: BlankNodeOrIRI;
    graph?: IRITerm;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface RmlEvaluationResult {
}

export interface BlankNodeOrIRI extends Term {
}

export interface BlankNodeTerm extends BlankNodeOrIRI {
    id: string;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface CollectionOrContainerTerm extends BlankNodeOrIRI {
    elements: Term[];
    id: BlankNodeOrIRI;
    idGenerated: boolean;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface Datardf {
}

export interface IRITerm extends BlankNodeOrIRI {
    uri: string;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface LiteralTerm extends Term {
    value: string;
    datatype?: IRITerm;
    language?: string;
    targets: LogicalTarget[];
    lineage?: OriginLineage;
}

export interface RdfAltTerm extends CollectionOrContainerTerm {
}

export interface RdfBagTerm extends CollectionOrContainerTerm {
}

export interface RdfListTerm extends CollectionOrContainerTerm {
}

export interface RdfSeqTerm extends CollectionOrContainerTerm {
}

export interface Term extends RmlEvaluationResult {
}

export interface Point extends Comparable<Point> {
    line: number;
    column: number;
}

export interface PointRange extends Comparable<PointRange> {
    start: Point;
    end?: Point;
}

export interface Path extends Comparable<Path>, Iterable<Path>, Watchable {
}

export interface LanguageClient {
}

export interface TextDocumentService {
}

export interface WorkspaceService {
}

export interface MappingDocument extends AbstractPlanNode {
    triplesMaps: TriplesMap[];
    prefixMap: { [index: string]: string };
}

export interface RmlLoader {
    reflectiveLoader: ReflectiveRdfObjectLoader;
    mappingPath?: Path;
    mappingContent?: string;
    mappingModel?: Model;
    mappingDirectory: Path;
    currentDirectory: Path;
    report?: RmlExecutionReport;
    validationMode: ValidationMode;
    prefixMap: { [index: string]: string };
    mappingPaths: Path[];
}

export interface Model extends ModelCon, ModelGraphInterface, RDFReaderF, RDFWriterF, PrefixMapping, Lock {
}

export interface RmlExecutionReport {
    errors: RmlError[];
    executionPlan: MappingDocument;
    statistics: Statistics;
}

export interface PlanNode {
}

export interface AstKey {
}

export interface Iteration {
    nulls: any[];
    lineage?: OriginLineage;
}

export interface EvaluationContext {
    report: RmlExecutionReport;
    baseIri?: string;
    blankNodeScope: BlankNodeScope;
    activeTargets: LogicalTarget[];
    cancellationToken: CancellationToken;
    sourceIterations: { [index: string]: Iteration[] };
    nodeOverrides: { [index: string]: RmlEvaluationResult[] };
    joinIndexes: { [index: string]: ParentJoinIndex };
    validationMode: ValidationMode;
    templateSafety: TemplateReferenceSafety;
}

export interface Range {
    start: Position;
    end: Position;
}

export interface Resource extends RDFNode {
}

export interface Exception extends Throwable {
}

export interface Position {
    line: number;
    character: number;
}

export interface TextDocumentIdentifier {
    uri: string;
}

export interface OriginLineage {
    primary: IterationId;
    joined: IterationId[];
}

export interface RdfStatementLike extends RmlEvaluationResult {
}

export interface Watchable {
}

export interface AbstractPlanNode extends PlanNode {
    parent?: PlanNode;
    cachedAstKey?: AstKey;
    propertyOrigins: { [index: string]: Origin };
    extraOrigins: Origin[];
}

export interface ReflectiveRdfObjectLoader {
    context: RdfLoaderContext;
    typeDeciders: TypeDecider[];
    customMappers: { [index: string]: CustomMapper<any> };
    postProcessors: { [index: string]: PostProcessor<any>[] };
    typeBindings: { [index: string]: Class<any> };
    instanceCache: { [index: string]: any };
    metadataCache: { [index: string]: ClassMetadata };
    accessedStatements: Statement[];
}

export interface ModelCon {
}

export interface ModelGraphInterface {
}

export interface RDFReaderF {
}

export interface RDFWriterF {
}

export interface PrefixMapping {
}

export interface Lock {
}

export interface Statistics {
    generatedStatementPerTriplesMap: { [index: string]: number };
    generatedStatements: number;
}

export interface Cache<K, V> {
}

export interface ParentJoinIndex {
    unconditional: boolean;
    allParentIterations: Iteration[];
    index: Multimap<CompositeJoinKey, Iteration>;
}

export interface BlankNodeScope {
    counter: number;
    idMap: { [index: string]: string };
}

export interface CancellationToken {
    cancelled: AtomicBoolean;
}

export interface RDFNode extends FrontsNode {
}

export interface Throwable extends Serializable {
    detailMessage: string;
    cause: Throwable;
    stackTrace: StackTraceElement[];
    suppressedExceptions: Throwable[];
}

export interface IterationId {
    logicalSourceId: string;
    index: number;
}

export interface LogicalTarget {
    subject: Resource;
    target?: RMLTarget;
    serialization?: Resource;
    compression?: Resource;
    encoding?: Resource;
}

export interface Comparable<T> {
}

export interface Iterable<T> {
}

export interface TriplesMap extends AbstractPlanNode, BaseIRIScope, LocalReferenceScope, LogicalTargetScope {
    subject: Resource;
    logicalSource: AbstractLogicalSource;
    subjectMap: SubjectMap;
    predicateObjectMaps: PredicateObjectMap[];
    baseIRI: string;
    logicalTargets: LogicalTarget[];
    countGeneratedStatements: number;
}

export interface Origin {
    sourceStatements?: RDFPointer[];
}

export interface RdfLoaderContext {
}

export interface RmlError {
    message: string;
    origin?: Origin;
    errorType: OntClass;
    exception?: Exception;
    context: { [index: string]: any };
    planNode?: PlanNode;
}

export interface AtomicBoolean extends Serializable {
    value: number;
}

export interface FrontsNode {
}

export interface Serializable {
}

export interface StackTraceElement extends Serializable {
    classLoaderName: string;
    moduleName: string;
    moduleVersion: string;
    declaringClass: string;
    methodName: string;
    fileName: string;
    lineNumber: number;
    format: number;
}

export interface RMLTarget {
}

export interface AbstractLogicalSource extends AbstractPlanNode, FormulationIterable, FieldParent, ExportedReferenceScope, LogicalTargetScope {
    subject: Resource;
    nulls: any[];
    logicalTargets: LogicalTarget[];
}

export interface SubjectMap extends TermMap {
    classes: Resource[];
    graphMaps: GraphMap[];
}

export interface BaseIRIScope extends PlanNode {
}

export interface LocalReferenceScope extends PlanNode {
}

export interface LogicalTargetScope extends PlanNode {
}

export interface TypeDecider {
}

export interface CustomMapper<T> {
}

export interface PostProcessor<T> {
}

export interface Class<T> extends Serializable, GenericDeclaration, Type, AnnotatedElement, OfField<Class<any>>, Constable {
    componentType: Class<any>;
}

export interface ClassMetadata {
    idField: Field;
    properties: PropertyMetadata[];
    lists: ListPropertyMetadata[];
    mappedFrom: MappedFromMetadata[];
    origins: OriginMetadata[];
    originPredicates: OriginPredicateMetadata[];
    rdfTypeUri?: string;
}

export interface Statement extends FrontsTriple {
}

export interface OntClass extends OntResource {
}

export interface Multimap<K, V> {
}

export interface CompositeJoinKey {
    keys: CanonicalJoinKey[];
}

export interface FormulationIterable extends PlanNode {
}

export interface FieldParent {
}

export interface ExportedReferenceScope extends PlanNode {
}

export interface DatatypeMap extends TermMap {
}

export interface LanguageMap extends ExpressionMap {
}

export interface GatherMap extends AbstractPlanNode, LogicalTargetScope {
    subject: Resource;
    logicalTargets: LogicalTarget[];
    allowEmptyListAndContainer: boolean;
    gatherAs?: Resource;
    strategy: Resource;
    gatherMaps: TermGenerator[];
}

export interface Expression extends PlanNode {
}

export interface Condition extends ExpressionMap {
}

export interface TermMap extends ExpressionMap, TermGenerator {
    datatypeMap?: DatatypeMap;
    languageMap?: LanguageMap;
    termType?: Resource;
    gatherMap?: GatherMap;
    baseIRI?: string;
}

export interface PredicateObjectMap extends AbstractPlanNode, LogicalTargetScope {
    subject: Resource;
    logicalTargets: LogicalTarget[];
    predicateMaps: PredicateMap[];
    objectMaps: BaseObjectMap[];
    graphMaps: GraphMap[];
}

export interface RDFPointer {
}

export interface GenericDeclaration extends AnnotatedElement {
}

export interface Type {
}

export interface AnnotatedElement {
}

export interface Constable {
}

export interface Field extends AccessibleObject, Member {
}

export interface FrontsTriple {
}

export interface OntResource extends Resource {
}

export interface GraphMap extends TermMap {
}

export interface ExpressionMap extends AbstractPlanNode, LogicalTargetScope {
    subject: Resource;
    expression: Expression;
    condition: Condition;
    logicalTargets: LogicalTarget[];
}

export interface TermGenerator extends PlanNode {
}

export interface OfField<F> extends TypeDescriptor {
}

export interface AccessibleObject extends AnnotatedElement {
}

export interface Member {
}

export interface PropertyMetadata {
    field: Field;
    propertyUri: string;
    required: boolean;
    shortcutPropertyUri: string;
    targetPropertyUri: string;
    genericType: Class<any>;
}

export interface ListPropertyMetadata {
    field: Field;
    propertyUri: string;
    genericType: Class<any>;
}

export interface MappedFromMetadata {
    field: Field;
    candidateClasses: Class<any>[];
}

export interface OriginMetadata {
    field: Field;
    targetPropertyName: string;
    part: StatementPart;
}

export interface OriginPredicateMetadata {
    field: Field;
    targetPropertyName: string;
}

export interface CanonicalJoinKey {
    normalizedValue: any;
}

export interface PredicateMap extends TermMap {
}

export interface BaseObjectMap extends PlanNode, TermGenerator {
}

export interface TypeDescriptor {
}

export type ErrorKind = "CONNECTION_ERROR" | "QUERY_ERROR";

export type PlanNodeStage = "LOGICAL_SOURCE" | "REFERENCE" | "INPUT_PARAMETER" | "FUNCTION" | "TEMPLATE_EXPRESSION" | "TERM_MAP" | "PREDICATE_OBJECT_MAP" | "TRIPLES_MAP" | "OTHER";

export type DiagnosticSeverity = "Error" | "Warning" | "Information" | "Hint";

export type ValidationMode = "STRICT" | "LENIENT" | "NONE";

export type TemplateReferenceSafety = "UNSAFE" | "SAFE_IRI" | "SAFE_URI";

export type StatementPart = "Subject" | "Predicate" | "Object";
