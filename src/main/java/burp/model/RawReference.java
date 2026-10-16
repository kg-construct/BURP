package burp.model;

import burp.parse.ValidationMode;
import burp.parse.turtleprov.OffsetMapper;
import burp.parse.turtleprov.Point;
import burp.reporting.*;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represent any reference. It must be compiled before it can be executed.
 * Usually compilation is triggered by {@link #compileReferences()}.
 * And it then stores the logical source constructed reference.
 */
public class RawReference extends Reference implements ReferenceHolder {
    private Reference compiledReference = null;

    public RawReference() {
        super();
    }

    public RawReference(String reference) {
        super(reference);
    }

    public RawReference(String reference, @Nullable Origin origin) {
        super(reference, origin);
    }

    public Reference getCompiledReference() {
        return compiledReference;
    }

    @Override
    public Iterable<PlanNode> children() {
        if (compiledReference != null)
            return List.of(compiledReference);
        else return Collections.emptyList();
    }

    public void setCompiledReference(Reference compiledReference) {
        this.compiledReference = compiledReference;
    }

    @Override
    public List<Object> getValues(Iteration i) {
        // compiledReference may be null when compilation failed, for example, in LENIENT mode.
        if (compiledReference == null) return Collections.emptyList();
        return compiledReference.getValues(i);
    }

    @Override
    public void compileReferences() {
        if (compiledReference == null && reference != null && !reference.isBlank()) {
            LocalReferenceScope scope = ancestor(LocalReferenceScope.class);
            if (scope != null) {
                try {
                    compiledReference = scope.buildLocalReference(reference, originOf("reference"));
                } catch (BurpException be) {
                    throw adjustErrorProvenance(be);
                } catch (Exception ignored) {
                }
            }
        }
    }


    /**
     * Adjusts the provenance information of a given {@link BurpException} by modifying error ranges
     * in its associated {@link RmlError}, to reflect characters' offsets introduced by escaping \{ \} in template reference segments.
     *
     * @param be the {@link BurpException} whose provenance information is to be adjusted; if
     *           the associated {@link RmlError} or its provenance information is null, the exception
     *           is returned unchanged.
     * @return a new {@link BurpException} instance with updated provenance information if adjustments
     * were necessary, or the original exception if no changes were made.
     */
    private BurpException adjustErrorProvenance(BurpException be) {
        RmlError err = be.getError();
        if (err == null || err.origin() == null || err.origin().sourceStatements() == null) {
            return be;
        }
        Template.ReferenceSegment seg = ancestor(Template.ReferenceSegment.class);
        if (seg == null) {
            return be;
        }
        OffsetMapper mapper = seg.innerOffsetMapper();
        List<RDFPointer> updatedPointers = new ArrayList<>();
        boolean modified = false;

        for (RDFPointer ptr : err.origin().sourceStatements()) {
            if (ptr instanceof LiteralPart(Statement stmt, PointRange objectRange)) {
                PointRange origRange = seg.range();
                Point currentStart = objectRange.start();
                Point baseStart = origRange.start();

                if (currentStart.line() == baseStart.line() && currentStart.column() >= baseStart.column()) {
                    int unescapedOffset = currentStart.column() - baseStart.column();
                    int templateOffset = mapper.toRawOffset(unescapedOffset);
                    if (templateOffset != unescapedOffset) {
                        Point adjustedStart = baseStart.plus(new Point(0, templateOffset));
                        PointRange adjustedRange = new PointRange(adjustedStart, objectRange.end());
                        updatedPointers.add(new LiteralPart(stmt, adjustedRange));
                        modified = true;
                        continue;
                    }
                }
            }
            updatedPointers.add(ptr);
        }

        if (!modified) {
            return be;
        }
        Origin adjustedOrigin = new Origin(updatedPointers);
        RmlError adjustedError = new RmlError(
                err.message(),
                adjustedOrigin,
                err.errorType(),
                err.exception(),
                err.context(),
                err.planNode()
        );
        return new BurpException(adjustedError);
    }

    @Override
    public List<String> getStrings(Iteration i) {
        if (compiledReference == null) return Collections.emptyList();
        return compiledReference.getStrings(i);
    }

    @Override
    public String getNodeType() {
        if (compiledReference == null) {
            compileReferences();
        }
        if (compiledReference != null) {
            return compiledReference.getClass().getSimpleName();
        }
        LocalReferenceScope scope = ancestor(LocalReferenceScope.class);
        if (scope instanceof AbstractLogicalSource als) {
            String srcName = als.getClass().getSimpleName();
            return srcName.endsWith("Source") ? srcName.substring(0, srcName.length() - 6) + "Reference" : srcName + "Reference";
        }
        return super.getNodeType();
    }
}
