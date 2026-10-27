package turtleprov;

import burp.parse.turtleprov.OffsetMapper;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
public class OffsetMapperTest {

    @Test
    public void testIdentityMapper() {
        OffsetMapper identity = OffsetMapper.identity();

        assertThat(identity.toRawOffset(0)).isEqualTo(0);
        assertThat(identity.toRawOffset(42)).isEqualTo(42);
        assertThat(identity.encode()).isNull();

        OffsetMapper decoded = OffsetMapper.decode(null);
        assertThat(decoded.toRawOffset(10)).isEqualTo(10);
        assertThat(decoded.encode()).isNull();
    }

    @Test
    public void testRangeOffsetMapperAndSerializationRoundTrip() {
        // Simulates: raw "a\"b\"c" -> decoded "a\"b\"c"
        // After first \" at decoded index 2: drift is +1
        // After second \" at decoded index 4: drift is +2
        OffsetMapper.Builder builder = OffsetMapper.builder();
        builder.recordEscape(2, 2, 1);
        builder.recordEscape(4, 2, 1);
        OffsetMapper mapper = builder.build();

        // Query offsets:
        // Decoded 0 ('a') -> raw 0
        assertThat(mapper.toRawOffset(0)).isEqualTo(0);
        // Decoded 1 ('"') -> before first threshold (2), so raw 1
        assertThat(mapper.toRawOffset(1)).isEqualTo(1);
        // Decoded 2 ('b') -> at/after threshold 2, drift +1 -> raw 3
        assertThat(mapper.toRawOffset(2)).isEqualTo(3);
        // Decoded 3 ('"') -> after threshold 2, before threshold 4 -> raw 4
        assertThat(mapper.toRawOffset(3)).isEqualTo(4);
        // Decoded 4 ('c') -> at/after threshold 4, drift +2 -> raw 6
        assertThat(mapper.toRawOffset(4)).isEqualTo(6);

        // Serialization round-trip
        String encoded = mapper.encode();
        assertThat(encoded).isEqualTo("2:1;4:2");

        OffsetMapper restored = OffsetMapper.decode(encoded);
        assertThat(restored.toRawOffset(0)).isEqualTo(0);
        assertThat(restored.toRawOffset(2)).isEqualTo(3);
        assertThat(restored.toRawOffset(4)).isEqualTo(6);
        assertThat(restored.encode()).isEqualTo(encoded);
    }

    @Test
    public void testComposeInnerAndOuterMappers() {
        // Outer literal: Turtle token '\u0041/{a\}b}'
        // Escapes:
        // 1. '\u0041' at outer decoded index 1, raw length 6 ('\u0041'), decoded length 1 ('A') -> drift +5
        OffsetMapper.Builder outerBuilder = OffsetMapper.builder();
        outerBuilder.recordEscape(1, 6, 1);
        OffsetMapper outer = outerBuilder.build();
        assertThat(outer.encode()).isEqualTo("1:5");

        // Reference segment starts at outer decoded offset 3 ('baseOffset = 3')
        int baseOffset = 3;

        // Inner template reference expression: 'a\}b'
        // Escapes:
        // 1. '\}' at inner decoded index 2, raw length 2 ('\}'), decoded length 1 ('}') -> drift +1
        OffsetMapper.Builder innerBuilder = OffsetMapper.builder();
        innerBuilder.recordEscape(2, 2, 1);
        OffsetMapper inner = innerBuilder.build();
        assertThat(inner.encode()).isEqualTo("2:1");

        OffsetMapper composed = OffsetMapper.compose(inner, outer, baseOffset);

        // Inner decoded offset 0 ('a'):
        // inner raw = 0 -> outer pos 3 -> outer raw = 8 -> delta = 8 - 8 = 0
        assertThat(composed.toRawOffset(0)).isEqualTo(0);

        // Inner decoded offset 1 ('}'):
        // inner raw = 1 -> outer pos 4 -> outer raw = 9 -> delta = 9 - 8 = 1
        assertThat(composed.toRawOffset(1)).isEqualTo(1);

        // Inner decoded offset 2 ('b'):
        // inner raw = 3 (due to '\}' escape) -> outer pos 6 -> outer raw = 11 (due to outer '\u0041' escape)
        // delta = 11 - 8 = 3 (accounts for both escapes!)
        assertThat(composed.toRawOffset(2)).isEqualTo(3);
    }

    @Test
    public void testComposeWithIdentityOuterReturnsInner() {
        OffsetMapper.Builder innerBuilder = OffsetMapper.builder();
        innerBuilder.recordEscape(3, 2, 1);
        OffsetMapper inner = innerBuilder.build();

        OffsetMapper composedWithNull = OffsetMapper.compose(inner, null, 10);
        assertThat(composedWithNull).isSameAs(inner);

        OffsetMapper composedWithIdentity = OffsetMapper.compose(inner, OffsetMapper.identity(), 10);
        assertThat(composedWithIdentity).isSameAs(inner);
    }

    @Test
    public void testArrayOffsetMapperDirect() {
        // Simulates: raw "a\"b\"c" (length 7) -> decoded "a\"b\"c" (length 5)
        // raw offsets for decoded indices 0..4: [0, 1, 3, 4, 6]
        int[] rawOffsets = {0, 1, 3, 4, 6};
        OffsetMapper mapper = OffsetMapper.fromArray(rawOffsets);

        assertThat(mapper.toRawOffset(0)).isEqualTo(0);
        assertThat(mapper.toRawOffset(1)).isEqualTo(1);
        assertThat(mapper.toRawOffset(2)).isEqualTo(3);
        assertThat(mapper.toRawOffset(3)).isEqualTo(4);
        assertThat(mapper.toRawOffset(4)).isEqualTo(6);

        // Past array bounds: extrapolates using last drift (+2)
        assertThat(mapper.toRawOffset(5)).isEqualTo(7);
        assertThat(mapper.toRawOffset(6)).isEqualTo(8);

        // Negative offset
        assertThat(mapper.toRawOffset(-1)).isEqualTo(-1);

        // Serialization into standard compact format
        assertThat(mapper.encode()).isEqualTo("2:1;4:2");
    }

    @Test
    public void testVectorBuilder() {
        OffsetMapper.VectorBuilder builder = OffsetMapper.vectorBuilder();
        builder.append(0);
        builder.append(1);
        builder.append(3);
        builder.append(4);
        builder.append(6);
        OffsetMapper mapper = builder.build();

        assertThat(mapper.toRawOffset(0)).isEqualTo(0);
        assertThat(mapper.toRawOffset(2)).isEqualTo(3);
        assertThat(mapper.toRawOffset(4)).isEqualTo(6);
        assertThat(mapper.encode()).isEqualTo("2:1;4:2");
    }

    @Test
    public void testBuilderBuildVectorMatchesRangeMapper() {
        OffsetMapper.Builder builder = OffsetMapper.builder();
        builder.recordEscape(2, 2, 1);
        builder.recordEscape(4, 2, 1);

        OffsetMapper rangeMapper = builder.build();
        OffsetMapper vectorMapper = builder.buildVector(4);

        for (int i = 0; i <= 5; i++) {
            assertThat(vectorMapper.toRawOffset(i)).isEqualTo(rangeMapper.toRawOffset(i));
        }
        assertThat(vectorMapper.encode()).isEqualTo(rangeMapper.encode());
    }

    @Test
    public void testVectorOffsetMapperDirect() {
        Vector<Integer> vec = new Vector<>();
        vec.add(0);
        vec.add(1);
        vec.add(3);
        vec.add(4);
        vec.add(6);
        OffsetMapper mapper = OffsetMapper.fromVector(vec);

        assertThat(mapper.toRawOffset(0)).isEqualTo(0);
        assertThat(mapper.toRawOffset(1)).isEqualTo(1);
        assertThat(mapper.toRawOffset(2)).isEqualTo(3);
        assertThat(mapper.toRawOffset(3)).isEqualTo(4);
        assertThat(mapper.toRawOffset(4)).isEqualTo(6);
        assertThat(mapper.toRawOffset(5)).isEqualTo(7);
        assertThat(mapper.encode()).isEqualTo("2:1;4:2");
    }

    @Test
    public void testComposeWithVectorOffsetMapper() {
        // Outer literal: raw '\u0041/{a\}b}' -> decoded 'A/{a\}b}' (baseOffset = 3)
        // \u0041 has raw length 6, decoded length 1 ('A') -> drift +5 from index 1
        // Outer raw offsets for indices 0..7: [0, 6, 7, 8, 9, 10, 11, 12]
        Vector<Integer> outerVec = new Vector<>();
        for (int v : new int[]{0, 6, 7, 8, 9, 10, 11, 12}) {
            outerVec.add(v);
        }
        OffsetMapper outerVector = OffsetMapper.fromVector(outerVec);

        // Inner reference: raw "a\}b" -> decoded "a}b"
        // raw offsets for decoded indices 0..2: [0, 1, 3]
        Vector<Integer> innerVec = new Vector<>();
        for (int v : new int[]{0, 1, 3}) {
            innerVec.add(v);
        }
        OffsetMapper innerVector = OffsetMapper.fromVector(innerVec);

        OffsetMapper composed = OffsetMapper.compose(innerVector, outerVector, 3);

        // Inner decoded offset 0 ('a') -> 0
        assertThat(composed.toRawOffset(0)).isEqualTo(0);
        // Inner decoded offset 1 ('}') -> 1
        assertThat(composed.toRawOffset(1)).isEqualTo(1);
        // Inner decoded offset 2 ('b') -> 3
        assertThat(composed.toRawOffset(2)).isEqualTo(3);
    }
}
