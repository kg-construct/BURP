package burp.dev;

import burp.dev.rml.service.MappingWorkspace;
import burp.model.MappingDocument;
import burp.model.TriplesMap;
import burp.model.key.AstKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AstKeyTest {

    @Test
    public void testAstKeyDeterminism() {
        AstKey.ConstantKey c1 = new AstKey.ConstantKey("http://example.com/test");
        AstKey.ConstantKey c2 = new AstKey.ConstantKey("http://example.com/test");
        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());

        AstKey.ReferenceKey r1 = new AstKey.ReferenceKey("name");
        AstKey.ReferenceKey r2 = new AstKey.ReferenceKey("name");
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());

        AstKey.TemplateKey t1 = new AstKey.TemplateKey("http://example.com/{name}", List.of(r1));
        AstKey.TemplateKey t2 = new AstKey.TemplateKey("http://example.com/{name}", List.of(r2));
        assertThat(t1).isEqualTo(t2);
        assertThat(t1.hashCode()).isEqualTo(t2.hashCode());

        AstKey.TriplesMapKey tm1 = new AstKey.TriplesMapKey(List.of(t1));
        AstKey.TriplesMapKey tm2 = new AstKey.TriplesMapKey(List.of(t2));
        assertThat(tm1).isEqualTo(tm2);
        assertThat(tm1.hashCode()).isEqualTo(tm2.hashCode());
    }

    @Test
    public void testMultiParseMappingAstKeyDeterminism() throws Exception {
        String mappingPath = "src/test/resources/perf/incremental/pom_predicate_change/mapping_base.ttl";
        MappingWorkspace ws1 = new MappingWorkspace();
        MappingWorkspace ws2 = new MappingWorkspace();

        MappingDocument doc1 = ws1.loadMappingWithStableIds(mappingPath);
        MappingDocument doc2 = ws2.loadMappingWithStableIds(mappingPath);

        assertThat(doc1.triplesMaps).hasSameSizeAs(doc2.triplesMaps);
        AstKey key1 = doc1.triplesMaps.getFirst().computeAstKey();
        AstKey key2 = doc2.triplesMaps.getFirst().computeAstKey();

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }
}
