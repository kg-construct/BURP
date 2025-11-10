package burp.model.fnmlutil

interface RMLFunction {
    val name: String
    fun apply(parameters: Map<String, Any?>): List<Return>
}

class RMLFunctionException(message: String, throwable: Throwable, function: RMLFunction) :
    RuntimeException(message, throwable)