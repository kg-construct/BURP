package burp.ls;

import burp.model.Iteration;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;

import java.util.Collections;
import java.util.List;

public class CSVReference extends Reference {
    public CSVReference(String reference, Origin origin) {
        super(reference, origin);
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof CSVIteration csvIteration)) {
            throw new IllegalArgumentException("CSVReference " + reference + " can only be used with CSVIteration.");
        }

        if (!csvIteration.map.containsKey(reference)) {
            String availableRefs = String.join(", ", csvIteration.map.keySet());
            throw new BurpException(
                new RmlError(
                    "Attribute " + reference + " does not exist.\n" +
                    "Available references are: " + availableRefs,
                    origin,
                    RER.ReferenceFormulationExecutionError
                )
            );
        }

        Object o = csvIteration.map.get(reference);

        return csvIteration.nulls.contains(o) ? Collections.emptyList() : Collections.singletonList(o);
    }
}
