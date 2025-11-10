package burp.model.fnmlutil

import com.google.auto.service.AutoService
import org.apache.commons.lang3.Strings
import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.text.WordUtils
import org.apache.jena.rdf.model.Literal
import org.apache.jena.vocabulary.XSD
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@AutoService(RMLFunction::class)
class HelloWorldFunction : RMLFunction {
    override val name = "http://example.com/functions/helloworld"

    override fun apply(parameters: Map<String, Any?>): List<Return> {
        return listOf(Return("Hello World!"))
    }
}

@AutoService(RMLFunction::class)
class SchemaFunction : RMLFunction {
    override val name = "http://example.com/functions/schema"

    override fun apply(parameters: Map<String, Any?>): List<Return> {
        val s = parameters["http://example.com/functions/stringParameter"].toString()
        val out = "https://schema.org/$s"

        val result = Return(out)
        result.put("http://example.com/functions/stringOutput", out)
        return listOf(result)
    }
}

@AutoService(RMLFunction::class)
class ParseURL : RMLFunction {
    override val name = "http://example.com/functions/parseURL"

    override fun apply(parameters: Map<String, Any?>): List<Return> {
        val s = parameters["http://example.com/functions/stringParameter"].toString()

        try {
            val l: MutableList<Return> = ArrayList<Return>()
            val url = URI(s).toURL()

            val protocol = url.protocol
            val domain = url.host
            val path = url.path

            val r = Return(path)
            r.put("http://example.com/functions/stringOutput", path)
            r.put("http://example.com/functions/protocolOutput", protocol)
            r.put("http://example.com/functions/domainOutput", domain)
            l.add(r)

            return l
        } catch (e: Exception) {
            throw RMLFunctionException("Invalid URL given as input" + s, e, this)
        }
    }
}


@AutoService(RMLFunction::class)
class UUIDFunction : RMLFunction {
    override val name = "https://github.com/morph-kgc/morph-kgc/function/built-in.ttl#uuid"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            return listOf(Return(UUID.randomUUID().toString()))
        } catch (e: Exception) {
            throw RuntimeException("Problem calling function UUID.", e)
        }
    }
}

// GREL functions
// https://openrefine.org/docs/manual/grelfunctions
@AutoService(RMLFunction::class)
class BooleanAndFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_and"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val a = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_a"] as Literal
            val b = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_b"] as Literal
            val out = a.boolean && b.boolean
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RuntimeException("Problem calling function boolean_and.", e)
        }
    }
}

@AutoService(RMLFunction::class)
class BooleanNotFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_not"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val a = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool"] as Literal
            val out = !a.boolean
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RuntimeException("Problem calling function boolean_not.", e)
        }
    }
}

@AutoService(RMLFunction::class)
class BooleanOrFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_or"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val a = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_a"] as Literal
            val b = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_b"] as Literal
            val out = a.boolean || b.boolean
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RuntimeException("Problem calling function boolean_or.", e)
        }
    }
}

@AutoService(RMLFunction::class)
class BooleanXorFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#boolean_xor"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val a = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_a"] as Literal
            val b = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_bool_b"] as Literal
            val out = a.boolean xor b.boolean
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RuntimeException("Problem calling function boolean_xor.", e)
        }
    }
}

@AutoService(RMLFunction::class)
class StringChompFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_chomp"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val f = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sep"].toString()
            val out = Strings.CS.removeEnd(s, f)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_chomp.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringContainsFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_contains"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val f = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub"].toString()
            val out = s.contains(f)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_contains.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringContainsPatternFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_contains_pattern"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val p = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_regex"].toString()
            val out = s.matches(p.toRegex())
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_contains_pattern.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class EndsWithFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#endsWith"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val f = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub"].toString()
            val out = s.endsWith(f)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function endsWith.", e, this)
        }
    }
}

// Escapes s in the given escaping mode. The mode can be one of: "html", "xml", "csv", "url", "javascript".
// Note that quotes are required around your mode. See the recipes for examples of escaping and unescaping.
@AutoService(RMLFunction::class)
class EscapeFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#escape"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val p = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#modeParam"].toString()
            var out: String? = null
            val modeLower = p.lowercase(Locale.getDefault())
            out = when (modeLower) {
                "html" -> StringEscapeUtils.escapeHtml4(s)
                "xml" -> StringEscapeUtils.escapeXml11(s)
                "csv" -> StringEscapeUtils.escapeCsv(s)
                "javascript" -> StringEscapeUtils.escapeEcmaScript(s)
                "url" -> URLEncoder.encode(s, StandardCharsets.UTF_8)
                else -> throw RMLFunctionException(
                    String.format("Mode %s not supported in GREL's escape function.", p),
                    Exception(),
                    this
                )
            }
            val r = Return(out)
            r.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(r)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function escape.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class LengthFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#length"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val out = s.length
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function length.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class MathAbsFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#math_abs"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n"] as Literal
            var out: Any? = null
            val duri = s.datatypeURI
            out = when {
                XSD.integer.uri == duri || XSD.xint.uri == duri -> abs(s.lexicalForm.toInt())
                XSD.xdouble.uri == duri -> abs(s.lexicalForm.toDouble())
                XSD.xlong.uri == duri -> abs(s.lexicalForm.toLong())
                XSD.xfloat.uri == duri -> abs(s.lexicalForm.toFloat())
                XSD.xshort.uri == duri -> abs(s.lexicalForm.toShort().toInt())
                else -> throw RMLFunctionException(
                    String.format("Mode %s not supported in GREL's abs function.", s),
                    Exception(),
                    this
                )
            }
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_decimal", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function math_abs.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class MathCeilFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#math_ceil"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n"] as Literal
            var out: Any? = null
            val duri = s.datatypeURI
            out = when {
                XSD.integer.uri == duri || XSD.xint.uri == duri -> ceil(s.lexicalForm.toInt().toDouble()).toInt()
                XSD.xdouble.uri == duri -> ceil(s.lexicalForm.toDouble()).toInt()
                XSD.xlong.uri == duri -> ceil(s.lexicalForm.toLong().toDouble()).toInt()
                XSD.xfloat.uri == duri -> ceil(s.lexicalForm.toFloat().toDouble()).toInt()
                XSD.xshort.uri == duri -> ceil(s.lexicalForm.toShort().toDouble()).toInt()
                else -> throw RMLFunctionException(
                    String.format("Mode %s not supported in GREL's ceil function.", s),
                    Exception(),
                    this
                )
            }
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function math_ceil.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class MathFloorFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#math_floor"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n"] as Literal
            var out: Any? = null
            val duri = s.datatypeURI
            out = when (duri) {
                XSD.integer.uri, XSD.xint.uri -> floor(s.lexicalForm.toInt().toDouble()).toInt()
                XSD.xdouble.uri -> floor(s.lexicalForm.toDouble()).toInt()
                XSD.xlong.uri -> floor(s.lexicalForm.toLong().toDouble()).toInt()
                XSD.xfloat.uri -> floor(s.lexicalForm.toFloat().toDouble()).toInt()
                XSD.xshort.uri -> floor(s.lexicalForm.toShort().toDouble()).toInt()
                else -> throw RMLFunctionException(
                    String.format("Mode %s not supported in GREL's floor function.", s),
                    Exception(),
                    this
                )
            }
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function math_floor.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StartsWithFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#startsWith"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val f = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub"].toString()
            val out = s.startsWith(f)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function starts_with.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringGetFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_get"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val from = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from"] as Literal
            val to = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to"] as Literal?
            var out: String? = null
            val f = from.int
            if (to != null) {
                val t = to.int
                if (t > 0) out = s.substring(f, t)
                else out = s.substring(f, s.length + t)
            } else out = s.substring(f, f + 1)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_get.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringReplaceFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_replace"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val f = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_find"].toString()
            val r = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#param_replace"].toString()
            val out = s.replace(f.toRegex(), r)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_replace.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringStripFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_strip"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val out = s.trim { it <= ' ' }
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_strip.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringSubstringFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_substring"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val from = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from"] as Literal
            val to = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to"] as Literal?
            var out: String? = null
            val f = from.int
            if (to != null) {
                val t = to.int
                if (t > 0) out = s.substring(f, t)
                else out = s.substring(f, s.length + t)
            } else out = s.substring(f)
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_substring.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class StringTrimFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#string_trim"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val out = s.trim { it <= ' ' }
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function string_trim.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class ToLowerCaseFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#toLowerCase"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val out = s.lowercase(Locale.getDefault())
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function toLowerCase.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class ToUpperCaseFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#toUpperCase"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val out = s.uppercase(Locale.getDefault())
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function toUpperCase.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class ToTitleCaseFunction : RMLFunction {
    override val name = "http://users.ugent.be/~bjdmeest/function/grel.ttl#toTitleCase"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val s = parameters["http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam"].toString()
            val out = WordUtils.capitalizeFully(s.lowercase(Locale.getDefault()))
            val re = Return(out)
            re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function toTitleCase.", e, this)
        }
    }
}

@AutoService(RMLFunction::class)
class ToUpperCaseURLFunction : RMLFunction {
    override val name = "http://example.com/idlab/function/toUpperCaseURL"
    override fun apply(parameters: Map<String, Any?>): List<Return> {
        try {
            val str = parameters["http://example.com/idlab/function/str"].toString().uppercase(Locale.getDefault())
            val out: String? = if (str.startsWith("HTTP://")) str else "http://" + str
            val re = Return(out)
            return listOf(re)
        } catch (e: Exception) {
            throw RMLFunctionException("Problem calling function toUpperCaseURL.", e, this)
        }
    }
}
