package com.soywiz.wasm

class WasmModule(
    val functions: List<WasmFunc>,
    val datas: List<WasmData>,
    val types: List<WasmType>,
    val globals: List<WasmGlobal>,
    val elements: List<WasmElement>
) {
    val functionsByName = functions.associateBy { it.name }
    val globalsByIndex = globals.associateBy { it.index }
    fun getFunction(item: Int): WasmFunc = functions[item]
    fun getFunction(item: String): WasmFunc = functionsByName[item] ?: error("Can't find function $item")
    fun getFunction(item: Any): WasmFunc {
        return when (item) {
            is Int -> getFunction(item)
            is String -> getFunction(item)
            else -> TODO("getFunction($item)")
        }
    }
}
