package burp.ls

import at.asitplus.jsonpath.JsonPath
import at.asitplus.jsonpath.core.JsonPathCompilerException
import at.asitplus.jsonpath.core.JsonPathQueryException
import at.asitplus.jsonpath.core.NodeListEntry
import burp.model.Iteration
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Paths

private class JSONSourceRFC : FileBasedLogicalSource() {
    override fun iterator(): Iterator<JSONIterationRFC> {
        try {
            val contents = Files.readString(Paths.get(getDecompressedFile()), encoding)
            val jsonContent = Json.parseToJsonElement(contents)
            val results = JsonPath(iterator).query(jsonContent)
            return results.map { JSONIterationRFC(it, nulls) }.iterator()
        } catch (e: Throwable) {
            throw RuntimeException(e)
        }
    }
}

class JSONIterationRFC(val json: NodeListEntry, nulls: Set<Any>) : Iteration(nulls) {

    override fun getValuesFor(reference: String?): List<Any?> {
        // We need to explicitly convert the objects
        // to strings because RML has not worked out
        // "6.6.1 Automatically deriving datatypes" yet
        val resultList: MutableList<Any?> = mutableListOf()
        try {
            val entries = JsonPath(reference ?: "").query(json.value)
            for (entry in entries) {
                when (val jsonElement = entry.value) {
                    is JsonArray -> throw RuntimeException("Data error: reference retrieved an array")
                    is JsonObject -> resultList.add(jsonElement.toString())
                    is JsonNull -> /* ignore nulls: https://kg-construct.github.io/rml-io/spec/docs/#null-values*/ {}
                    is JsonPrimitive -> {
                        val content =
                            if (jsonElement.isString) jsonElement.content
                            else
                                jsonElement.intOrNull
                                    ?: jsonElement.longOrNull
                                    ?: jsonElement.floatOrNull
                                    ?: jsonElement.doubleOrNull
                                    ?: jsonElement.booleanOrNull
                        if (content !in nulls) resultList.add(content)
                    }
                }
            }
        } catch (e: JsonPathCompilerException) {
            println("JSONPath Error in $reference:\n${e.message}")
            throw e
        }catch (e: JsonPathQueryException){
            println("JSONPath Error due to $reference:\n${e.message}")
            throw e
        }
        return resultList
    }

    override fun getStringsFor(reference: String?): List<String?> =
        getValuesFor(reference).map { it.toString() }.toList()

    override fun asString(): String {
        return json.value.toString()
    }


}