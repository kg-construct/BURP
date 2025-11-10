package burp.model.fnmlutil


class Return(defaultValue: Any?) {
    private val returns = mutableMapOf<String, Any?>()

    @JvmField
    var defaultValue: Any? = null

    init {
        this.defaultValue = defaultValue
    }

    fun get(key: String) = returns.getOrElse(key) {
        throw RuntimeException("ExecutionError: Unknown return value $key.")
    }


    fun put(key: String, value: Any?) = returns.put(key, value)

}
