package burp.ls;

import burp.model.Iteration;
import burp.model.Reference;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.jena.rdf.model.Resource;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CSVSource extends FileBasedLogicalSource {
    public char delimiter = ',';
    public char quoteChar = '"';
    public String commentPrefix = null;
    public boolean firstLineIsHeader = true;

    @Override
    public Iterable<Iteration> iterator() {
        try {
            if (iterations == null) {
                iterations = new ArrayList<>();

                FileInputStream fileReader = new FileInputStream(getDecompressedFile());
                BOMInputStream bomStream = BOMInputStream.builder().setInputStream(fileReader).get();
                InputStreamReader reader = new InputStreamReader(bomStream, encoding);

                CSVReader csvReader = new CSVReaderBuilder(reader)
                        .withCSVParser(
                                new CSVParserBuilder()
                                        .withSeparator(delimiter)
                                        .withQuoteChar(quoteChar)
                                        .build()
                        ).build();

                List<String[]> all = csvReader.readAll();
                csvReader.close();

                if (commentPrefix != null) {
                    all.removeIf(rec -> rec.length > 0 && rec[0] != null && rec[0].startsWith(commentPrefix));
                }

                if (all.isEmpty()) {
                    return iterations;
                }

                // IF THE FIRST LINE IS THE HEADER, REMOVE THE FIRST FROM CSV
                // OTHERWISE, CREATE A LIST OF NUMBERED COLUMNS STARTING FROM ONE
                String[] header;
                if (firstLineIsHeader) {
                    header = all.removeFirst();
                } else {
                    int columnCount = all.getFirst().length;
                    header = new String[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        header[i] = String.valueOf(i + 1);
                    }
                }

                for (String[] rec : all) {
                    iterations.add(new CSVIteration(header, rec, nulls));
                }
            }
            return iterations;
        } catch (BurpException e) {
            throw e;
        } catch (Exception e) {
            throw new BurpException(new RmlError(e.getMessage(), new Origin(this, null), RER.Error, e));
        }
    }

    @Override
    public Resource getReferenceFormulation() {
        return RML.CSV;
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return new CSVReference(reference, origin);
    }
}