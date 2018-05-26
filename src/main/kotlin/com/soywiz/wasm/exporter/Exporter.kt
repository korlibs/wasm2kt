package com.soywiz.wasm.exporter

import com.soywiz.wasm.*

interface Exporter {
    fun dump(config: ExportConfig): Indenter
}

data class ExportConfig(
    val className: String,
    val packageName: String = ""
)