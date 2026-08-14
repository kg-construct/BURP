package burp.ls;

import burp.vocabularies.RML;
import com.google.auto.service.AutoService;
import org.apache.jena.rdf.model.Resource;
import java.nio.file.Path;

@AutoService(LogicalSourceProvider.class)
public class RDBTableSourceProvider extends RDBSourceProvider {
    @Override
    public boolean supports(Resource referenceFormulation) {
        return RML.SQL2008Table.equals(referenceFormulation);
    }

    @Override
    public RDBSource create(Resource ls, Path mappingDirectory, Path currentWorkingDirectory) {
        RDBSource source = super.create(ls, mappingDirectory, currentWorkingDirectory);
        source.referenceFormulation = RML.SQL2008Table;
        source.query = "SELECT * FROM " + source.query;
        return source;
    }
}
