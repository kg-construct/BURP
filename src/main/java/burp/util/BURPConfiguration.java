package burp.util;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.jspecify.annotations.NonNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Command(name = "burp", description = "A Basic and Unassuming RML Processor (BURP)",
        mixinStandardHelpOptions = true, versionProvider = VersionProvider.class)
public class BURPConfiguration {

    @Option(names = {"-m", "--mappingFile"},
            description = "The RML mapping file(s) (can specify multiple with -m or --mappingFile)",
            arity = "0..*")
    public List<String> mappingFiles = new java.util.ArrayList<>();

    public String mappingFile;

    public List<String> getEffectiveMappingFiles() {
        if (mappingFiles != null && !mappingFiles.isEmpty()) return mappingFiles;
        if (mappingFile != null && !mappingFile.isBlank()) return List.of(mappingFile);
        return List.of();
    }

    @Option(names = {"-v", "--validate"}, description = "Validate the RML mapping against SHACL shapes without executing it")
    public boolean validate;

    @Option(names = {"--color"}, arity = "0..1", fallbackValue = "true", description = "Enable or disable ANSI colors in report (default: auto-detect)")
    public Boolean color;

    @Option(names = {"-d", "--dev"}, arity = "0..1", fallbackValue = "-1", description = "Run dev server on optional port")
    public Integer devPort;

    public boolean isDevServer() { return devPort != null; }

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