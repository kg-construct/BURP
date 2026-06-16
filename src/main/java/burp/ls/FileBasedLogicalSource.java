package burp.ls;

import burp.model.Iteration;
import burp.model.LogicalSource;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.StatementParts;
import burp.util.Util;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public abstract class FileBasedLogicalSource extends LogicalSource {

    public List<Iteration> iterations = null;

    public SourceFile file = null;

    public List<StatementParts> fileOriginStmts = Collections.emptyList();

    public Charset encoding = StandardCharsets.UTF_8;
    public Resource compression = RML.none;

    public String getDecompressedFile() {
        Origin origin = new Origin(this, fileOriginStmts);
        File fileFile = file != null ? file.getFile(fileOriginStmts) : null;
        if (fileFile == null || !fileFile.exists()) {
            throw new BurpException(new RmlError("SourceAccessError: Cannot obtain file " + file, origin, RER.SourceAccessError));
        }
        return Util.getDecompressedFile(fileFile.getAbsolutePath(), compression);
    }
}