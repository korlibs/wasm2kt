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
    fun getFunction(item: Any): WasmFunc {
        return when (item) {
            is Int -> functions[item]
            is String -> functionsByName[item] ?: error("Can't find function $item")
            else -> TODO("getFunction($item)")
        }
    }
}
