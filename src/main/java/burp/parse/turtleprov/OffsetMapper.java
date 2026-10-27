package burp.parse.turtleprov;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/// Maps character offsets from an unescaped/decoded literal string back to the
/// corresponding character offsets in the raw Turtle source token.
///
/// Escape sequences (such as `\"`, `\\`, `\n`, unicode escapes) cause raw source tokens
/// to be longer than their decoded string values. This interface translates
/// coordinates between the two representations.
///
/// ### How It Works
///
/// Consider a raw Turtle token `a\"b\"c`:
///
/// | Representation | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
/// | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
/// | **Raw Source Token** | `a` | `\` | `"` | `b` | `\` | `"` | `c` |
/// | **Decoded String** | `a` | `"` | `b` | `"` | `c` | - | - |
/// | **Cumulative Drift** | 0 | 0 | +1 | +1 | +2 | - | - |
///
/// Each unescape step records a checkpoint `(decodedThreshold, cumulativeDrift)`:
/// - After first `\"` (decoded length 2): drift is `+1` (checkpoint `2:1`).
/// - After second `\"` (decoded length 4): drift is `+2` (checkpoint `4:2`).
///
/// Querying `toRawOffset(decodedOffset)` performs a binary search for the latest checkpoint
/// with `decodedThreshold <= decodedOffset` and returns `decodedOffset + cumulativeDrift`.
///
/// For any character produced by an escape sequence (e.g. `"` at decoded index 1), the threshold
/// is not yet satisfied (`2 <= 1` is false), so drift remains 0, mapping the character to the
/// start of the raw escape sequence (`\`). The cumulative drift takes effect starting at the
/// subsequent character (`decodedThreshold <= decodedOffset`), e.g. `toRawOffset(2)` (`'b'`) returns `2 + 1 = 3`.
///
/// ### Serialization (`encode` / `decode`)
///
/// The drift table is serialized into a compact string format:
/// ```
/// <threshold1>:<drift1>;<threshold2>:<drift2>;...
/// ```
/// For the example above, `encode()` yields `"2:1;4:2"`.
/// If there are no escapes (identity mapping), `encode()` returns `null`.
@NullMarked
public interface OffsetMapper {

    /// Translates a 0-based character index in the decoded string to the corresponding
    /// 0-based character index in the raw literal string.
    ///
    /// @param decodedOffset the 0-based character offset in the decoded string
    /// @return the 0-based character offset in the raw source token
    int toRawOffset(int decodedOffset);

    /// Serializes this mapper into a compact string representation for RDF annotation storage.
    ///
    /// @return a compact string representation, or `null` if this is an identity mapper
    @Nullable String encode();

    /// Returns an identity mapper for strings without escape sequences.
    static OffsetMapper identity() {
        return IdentityOffsetMapper.INSTANCE;
    }

    /// Creates an alternative vector-backed offset mapper from a vector of raw offsets.
    ///
    /// Inside the vector, `rawOffsets.get(decodedOffset)` directly stores the real character position
    /// in the raw source token, making `toRawOffset(i)` a direct O(1) element lookup.
    ///
    /// @param rawOffsets vector containing the real position in the source token for each decoded character
    /// @return a vector-backed offset mapper
    static OffsetMapper fromVector(Vector<Integer> rawOffsets) {
        return new VectorOffsetMapper(new Vector<>(rawOffsets));
    }

    /// Creates an alternative vector-backed offset mapper from an array of raw offsets.
    ///
    /// @param rawOffsets array containing the real position in the source token for each decoded character
    /// @return a vector-backed offset mapper
    static OffsetMapper fromArray(int[] rawOffsets) {
        Vector<Integer> vector = new Vector<>(rawOffsets.length);
        for (int offset : rawOffsets) {
            vector.add(offset);
        }
        return new VectorOffsetMapper(vector);
    }

    /// Creates a builder to directly build an alternative vector-backed offset mapper.
    static VectorBuilder vectorBuilder() {
        return new VectorBuilder();
    }

    /// Deserializes a mapper from its compact string representation.
    static OffsetMapper decode(@Nullable String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return identity();
        }
        String[] parts = encoded.split(";");
        List<DriftEntry> entries = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) continue;
            int colon = part.indexOf(':');
            if (colon > 0) {
                int threshold = Integer.parseInt(part.substring(0, colon));
                int drift = Integer.parseInt(part.substring(colon + 1));
                entries.add(new DriftEntry(threshold, drift));
            }
        }
        return entries.isEmpty() ? identity() : new RangeOffsetMapper(entries);
    }

    /// Composes an inner mapper (from template reference unescaping) with an outer mapper
    /// (from Turtle literal unescaping) starting at `baseOffset` in the outer decoded string.
    ///
    /// Literal templates are parsed in two stages:
    /// 1. Turtle literal unescaping (outer): decodes string escapes such as `\u0041`, `\n`, `\"`.
    /// 2. Template reference unescaping (inner): decodes curly brace escapes `\{` and `\}` within references `{...}`.
    ///
    /// Offsets are therefore shifted twice.
    ///
    /// ### Composition Example
    ///
    /// Consider a Turtle literal `'\u0041/{a\}b}'`:
    ///
    /// **1. Outer Literal Unescape:** raw token `'\u0041/{...}'` → decoded `'A/{...}'`
    /// The Unicode escape `\u0041` (6 raw characters) collapses to 1 decoded character `'A'`.
    /// At outer decoded index 1, checkpoint `1:5` is recorded (cumulative drift +5).
    ///
    /// | 0 | 1 | 2 | 3 (`baseOffset`) |
    /// | :---: | :---: | :---: | :---: |
    /// | `A` (from `\u0041`) | `/` | `{` | `a` |
    /// | 0 | +5 (checkpoint `1:5`) | +5 | +5 |
    ///
    /// **2. Inner Reference Unescape:** raw template segment `"a\}b"` → decoded expression `"a}b"`
    /// The escape `\}` (2 raw characters) collapses to 1 decoded character `'}'`.
    /// At inner decoded index 2, checkpoint `2:1` is recorded (cumulative drift +1).
    ///
    /// | 0 | 1 | 2 |
    /// | :---: | :---: | :---: |
    /// | `a` | `}` (from `\}`) | `b` |
    /// | 0 | 0 | +1 (checkpoint `2:1`) |
    ///
    static OffsetMapper compose(OffsetMapper inner, @Nullable OffsetMapper outer, int baseOffset) {
        if (outer == null || outer instanceof IdentityOffsetMapper) {
            return inner;
        }
        int rawBase = outer.toRawOffset(baseOffset);
        return new ComposedOffsetMapper(inner, outer, baseOffset, rawBase);
    }

    /// Creates a builder to track escape drifts during literal unescaping.
    static Builder builder() {
        return new Builder();
    }

    /// Single cumulative drift checkpoint.
    record DriftEntry(int decodedThreshold, int cumulativeDrift) {}

    /// Builder for accumulating offset drifts during unescaping.
    final class Builder {
        private final List<DriftEntry> entries = new ArrayList<>();
        private int cumulativeDrift = 0;

        /// Records that an escape sequence of length `rawLength` produced `decodedLength` characters.
        ///
        /// @param decodedEndOffset the decoded string offset immediately after the unescaped sequence
        /// @param rawLength the character length of the raw escape sequence in source Turtle
        /// @param decodedLength the character length of the unescaped replacement in decoded string
        public void recordEscape(int decodedEndOffset, int rawLength, int decodedLength) {
            int drift = rawLength - decodedLength;
            if (drift != 0) {
                cumulativeDrift += drift;
                entries.add(new DriftEntry(decodedEndOffset, cumulativeDrift));
            }
        }

        public OffsetMapper build() {
            if (entries.isEmpty()) {
                return identity();
            }
            return new RangeOffsetMapper(Collections.unmodifiableList(entries));
        }

        /// Builds an alternative vector-backed offset mapper up to the latest recorded threshold.
        public OffsetMapper buildVector() {
            return entries.isEmpty() ? identity() : buildVector(entries.getLast().decodedThreshold());
        }

        /// Builds an alternative vector-backed offset mapper for a decoded string of length `decodedLength`.
        public OffsetMapper buildVector(int decodedLength) {
            if (entries.isEmpty()) {
                return identity();
            }
            Vector<Integer> vector = new Vector<>(decodedLength + 1);
            RangeOffsetMapper rangeMapper = new RangeOffsetMapper(entries);
            for (int i = 0; i <= decodedLength; i++) {
                vector.add(rangeMapper.toRawOffset(i));
            }
            return new VectorOffsetMapper(vector);
        }
    }

    final class IdentityOffsetMapper implements OffsetMapper {
        private static final IdentityOffsetMapper INSTANCE = new IdentityOffsetMapper();

        private IdentityOffsetMapper() {}

        @Override
        public int toRawOffset(int decodedOffset) {
            return decodedOffset;
        }

        @Override
        public @Nullable String encode() {
            return null;
        }
    }

    final class RangeOffsetMapper implements OffsetMapper {
        private final List<DriftEntry> entries;

        public RangeOffsetMapper(List<DriftEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int toRawOffset(int decodedOffset) {
            if (decodedOffset < 0 || entries.isEmpty()) {
                return decodedOffset;
            }
            // Binary search to find the largest entry with decodedThreshold <= decodedOffset
            int low = 0;
            int high = entries.size() - 1;
            int matchIndex = -1;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                DriftEntry entry = entries.get(mid);
                if (entry.decodedThreshold() <= decodedOffset) {
                    matchIndex = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (matchIndex >= 0) {
                return decodedOffset + entries.get(matchIndex).cumulativeDrift();
            }
            return decodedOffset;
        }

        @Override
        public @Nullable String encode() {
            if (entries.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append(';');
                DriftEntry entry = entries.get(i);
                sb.append(entry.decodedThreshold()).append(':').append(entry.cumulativeDrift());
            }
            return sb.toString();
        }
    }

    final class ComposedOffsetMapper implements OffsetMapper {
        private final OffsetMapper inner;
        private final OffsetMapper outer;
        private final int baseOffset;
        private final int rawBase;

        public ComposedOffsetMapper(OffsetMapper inner, OffsetMapper outer, int baseOffset, int rawBase) {
            this.inner = inner;
            this.outer = outer;
            this.baseOffset = baseOffset;
            this.rawBase = rawBase;
        }

        @Override
        public int toRawOffset(int decodedOffset) {
            int innerRaw = inner.toRawOffset(decodedOffset);
            return outer.toRawOffset(baseOffset + innerRaw) - rawBase;
        }

        @Override
        public @Nullable String encode() {
            return null;
        }
    }

    /// Builder for constructing an alternative vector-backed offset mapper by appending positions.
    final class VectorBuilder {
        private final Vector<Integer> rawOffsets;

        public VectorBuilder() {
            this.rawOffsets = new Vector<>();
        }

        /// Appends the raw offset for the next decoded character.
        public VectorBuilder append(int rawOffset) {
            rawOffsets.add(rawOffset);
            return this;
        }

        /// Appends a contiguous range of `count` characters starting at `rawStart`.
        public VectorBuilder appendRange(int rawStart, int count) {
            for (int i = 0; i < count; i++) {
                rawOffsets.add(rawStart + i);
            }
            return this;
        }

        /// Builds the vector-backed offset mapper.
        public OffsetMapper build() {
            return new VectorOffsetMapper(rawOffsets);
        }
    }

    /// Alternative offset mapper implementation holding a vector/list of the literal's length
    /// where each index directly contains the real position in the raw source token.
    ///
    /// Querying `toRawOffset(decodedOffset)` is an O(1) element lookup into `rawOffsets`.
    final class VectorOffsetMapper implements OffsetMapper {
        private final Vector<Integer> rawOffsets;

        public VectorOffsetMapper(Vector<Integer> rawOffsets) {
            this.rawOffsets = rawOffsets;
        }

        public List<Integer> rawOffsets() {
            return rawOffsets;
        }

        @Override
        public int toRawOffset(int decodedOffset) {
            if (decodedOffset < 0) {
                return decodedOffset;
            }
            if (decodedOffset < rawOffsets.size()) {
                return rawOffsets.get(decodedOffset);
            }
            if (rawOffsets.isEmpty()) {
                return decodedOffset;
            }
            int lastIndex = rawOffsets.size() - 1;
            int lastDrift = rawOffsets.get(lastIndex) - lastIndex;
            return decodedOffset + lastDrift;
        }

        @Override
        public @Nullable String encode() {
            if (rawOffsets.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            int currentDrift = 0;
            boolean first = true;
            for (int i = 0; i < rawOffsets.size(); i++) {
                int drift = rawOffsets.get(i) - i;
                if (drift != currentDrift) {
                    currentDrift = drift;
                    if (!first) sb.append(';');
                    sb.append(i).append(':').append(currentDrift);
                    first = false;
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        }
    }
}
