package burp.vocabularies;

import burp.reporting.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RerTaxonomyTest {

    @Test
    void severityOfResolvesErrorWarningAndInformationFromRerTtl() {
        assertThat(RER.severityOf(RER.Error)).isEqualTo(Severity.ERROR);
        assertThat(RER.severityOf(RER.ReferenceFormulationSyntaxError)).isEqualTo(Severity.ERROR);
        assertThat(RER.severityOf(RER.IteratorSyntaxError)).isEqualTo(Severity.ERROR);

        assertThat(RER.severityOf(RER.Warning)).isEqualTo(Severity.WARNING);
        assertThat(RER.severityOf(RER.OntologyWarning)).isEqualTo(Severity.WARNING);
        assertThat(RER.severityOf(RER.OntologyDatatypeMismatch)).isEqualTo(Severity.WARNING);

        assertThat(RER.severityOf(RER.Information)).isEqualTo(Severity.INFORMATION);
    }

    @Test
    void superClassesOfIncludesTransitiveSuperClasses() {
        assertThat(RER.superClassesOf(RER.OntologyDatatypeMismatch))
                .containsExactlyInAnyOrder(
                        RER.OntologyWarning.getURI(),
                        RER.Warning.getURI(),
                        RER.Log.getURI()
                );

        assertThat(RER.superClassesOf(RER.IteratorSyntaxError))
                .contains(
                        RER.ReferenceFormulationSyntaxError.getURI(),
                        RER.LogicalSourceError.getURI(),
                        RER.MappingError.getURI(),
                        RER.Error.getURI(),
                        RER.Log.getURI()
                );
    }

    @Test
    void ptrAndRerConstantsAreInitialized() {
        assertThat(PTR.StatementPart.getURI()).isEqualTo(PTR.NS + "StatementPart");
        assertThat(PTR.statement.getURI()).isEqualTo(PTR.NS + "statement");
        assertThat(PTR.Subject.getURI()).isEqualTo(PTR.NS + "Subject");
        assertThat(RER.VERSION_INFO).isNotBlank();
    }
}
