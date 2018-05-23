package com.soywiz.wasm

interface WasmFuncResolver {
    fun resolveFunc(name: String): WasmFunc
}

interface WasmFuncRef {
    val name: String
    val func: WasmFunc
}

data class WasmFuncName(override val name: String, val resolve: (name: String) -> WasmFunc) : WasmFuncRef {
    override val func get() = resolve(name)
}

data class WasmFunc(
    val index: Int,
    val type: WasmType.Function,
    var code: Wasm.Code? = null,
    var import: Wasm.Import? = null,
    var export: Wasm.Export? = null
) : WasmFuncRef {
    override val func = this
    val rlocals by lazy { type.args + (code?.flatLocals ?: listOf()) }

    override val name: String by lazy { import?.name ?: export?.name ?: "f$index" }
}
