package burp.ls;

import burp.model.PlanNode;
import burp.parse.turtleprov.Point;
import burp.reporting.*;
import burp.vocabularies.RER;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
public record JSONPathError(@Nullable Point start, String message, boolean isSyntaxError) {
    @NonNull RmlError getRmlError(
            @NonNull Exception compilerException,
            @Nullable String jsonPath,
            @NonNull RDFGraphPointer jsonPathPtr,
            @NonNull PlanNode planNode
    ) {
        Origin origin = new Origin(List.of(jsonPathPtr));
        if (start != null) {
            LiteralPart literalPart;
            switch (jsonPathPtr) {
                case LiteralPart lp -> literalPart = lp;
                case StatementParts sp -> literalPart = sp.toLiteralPart();
                default -> literalPart = null;
            }

            if (literalPart != null) {
                PointRange newRange = literalPart.objectRange().plus(new PointRange(start));
                var newRangeLiteralPart = new LiteralPart(literalPart.stmt(), newRange);
                origin = new Origin(List.of(newRangeLiteralPart));
            }
        }

        return new RmlError(
                message,
                origin,
                isSyntaxError ? RER.ReferenceFormulationSyntaxError : RER.ReferenceFormulationExecutionError,
                compilerException,
                planNode
        );
    }
}
