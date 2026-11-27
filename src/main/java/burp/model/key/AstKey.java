package burp.model.key;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Deterministic, content-addressed AST keys for PlanNodes (Merkle DAG).
 * Replaces parent-dependent keys with bottom-up composition f(inputs, children).
 */
@NullMarked
public sealed interface AstKey {

    record DocumentKey(List<AstKey> children) implements AstKey {
    }

    record RootKey(String name) implements AstKey {
    }

    record TriplesMapKey(List<AstKey> children) implements AstKey {
    }

    record LogicalViewKey(List<AstKey> children) implements AstKey {
    }

    record LogicalSourceKey(String sourceFile, @Nullable String iteratorExpr) implements AstKey {
    }

    record PomKey(List<AstKey> children) implements AstKey {
    }

    record ConstantKey(String value) implements AstKey {
    }

    record ReferenceKey(String reference) implements AstKey {
    }

    record TemplateKey(String template, List<AstKey> children) implements AstKey {
    }

    record FieldKey(String fieldName, List<AstKey> children) implements AstKey {
    }

    record IterableFieldKey(String fieldName, @Nullable String iterator, @Nullable String referenceFormulation,
                            List<AstKey> children) implements AstKey {
    }

    record ExpressionMapKey(String termType, List<AstKey> children) implements AstKey {
    }

    record TermMapKey(String expression, String termType, @Nullable String extra,
                      List<AstKey> children) implements AstKey {
        public TermMapKey(String expression, String termType, @Nullable String extra) {
            this(expression, termType, extra, List.of());
        }

    }

    record JoinConditionKey(List<AstKey> children) implements AstKey {
    }

    record RefObjectMapKey(String parentTriplesMapIri, @Nullable AstKey parentSubjectKey,
                           List<AstKey> children) implements AstKey {
    }

    record ViewJoinKey(String parentLogicalViewIri, String joinType, List<AstKey> children) implements AstKey {
    }

    record GatherMapKey(String strategy, List<AstKey> children) implements AstKey {
    }

    record InputKey(String name, @Nullable AstKey child) implements AstKey {
    }

    record SourceFileKey(String pathOrUrl) implements AstKey {
    }

    record FunctionExecutionKey(String functionIri, List<AstKey> inputs) implements AstKey {
    }
}
