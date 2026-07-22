package burp.util;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.jspecify.annotations.NonNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.Iterator;

@Command(name = "burp", mixinStandardHelpOptions = true, description = "A Basic and Unassuming RML Processor (BURP)")
public class BURPConfiguration {

    @Option(names = {"-m", "--mappingFile"}, required = true, description = "The RML mapping file")
    public String mappingFile;

    @Option(names = {"-o", "--outputFile"}, description = "The output file")
    public String outputFile;

    @Option(names = {"-f", "--outputFormat"},
            description = "The format of the output file (default: deduced from output file)",
            completionCandidates = FormatCandidates.class)
    public String outputFormatString;

    public Lang getOutputFormat() {
        if (outputFormatString == null) return null;
        Lang lang = RDFLanguages.nameToLang(outputFormatString);
        if (lang == null) throw new IllegalArgumentException("Unknown output format: " + outputFormatString);
        return lang;
    }

    @Option(names = {"-b", "--baseIRI"}, description = "Used in resolving relative IRIs produced by the RML mapping",
            defaultValue = "http://example.org/")
    public String baseIRI = "http://example.org/";

    @Option(names = {"-r", "--reportFile"}, description = "The report file")
    public String reportFile;

    static class FormatCandidates implements Iterable<String> {
        @Override
        public @NonNull Iterator<String> iterator() {
            return Arrays.asList(
                    RDFLanguages.strLangNQuads, RDFLanguages.strLangTurtle, RDFLanguages.strLangTriG,
                    RDFLanguages.strLangJSONLD, RDFLanguages.strLangRDFXML
            ).iterator();
        }
    }
}