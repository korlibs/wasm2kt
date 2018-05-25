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
    var export: Wasm.Export? = null,
    var code2: Wasm.Code2? = null,
    val name2: String? = null
) : WasmFuncRef {
    fun getAst(wasm: WasmModule): Wast.Stm? = when {
        code != null -> {
            val body = code!!.body
            val bodyAst = body.toAst(wasm, func)
            bodyAst
        }
        code2 != null -> code2!!.body
        else -> null
    }

    override val func = this
    val rlocals: List<AstLocal> by lazy { type.args + (code?.flatLocals ?: listOf()) }

    override val name: String by lazy { name2 ?: import?.name ?: export?.name ?: "f$index" }
}
