package com.soywiz.wasm.exporter

import com.soywiz.wasm.*

class KotlinExporter(module: WasmModule) : Exporter(module) {
    override fun writeSetLocal(localName: String, expr: String) = Indenter { line("$localName = $expr") }
    override fun writeSetGlobal(globalName: String, expr: String) = Indenter { line("this.$globalName = $expr") }
}
