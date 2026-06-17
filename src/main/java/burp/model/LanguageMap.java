package burp.model;


import burp.model.rdf.LiteralTerm;
import burp.reporting.BurpException;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import org.apache.jena.langtagx.LangTagX;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LanguageMap extends ExpressionMap {

    /**
     * Generate valid Language Tags according to RFC 5646
     */
    public List<LanguageTag> generateLanguageTags(Iteration i) {
        Set<LogicalTarget> targets = getEffectiveTargets();
        return generateValues(i, TemplateReferenceSafety.UNSAFE)
                .stream()
                .filter(Objects::nonNull)
                .map(it -> {
                    String string;
                    if (it instanceof LiteralTerm literalTerm) {
                        string = literalTerm.value();
                    } else {
                        string = it.toString();
                    }

                    if (LangTagX.checkLanguageTag(string)) {
                        return new LanguageTag(string, targets);
                    } else {
                        throw new BurpException(
                                new RmlError(
                                        "Invalid language code: " + it,
                                        getExpressionOrigin(),
                                        RER.InvalidLanguageTagError
                                )
                        );
                    }
                })
                .collect(Collectors.toList());
    }
}