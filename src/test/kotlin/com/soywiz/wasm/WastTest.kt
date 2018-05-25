package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.vfs.*
import org.junit.*

class WastTest {
    @Test
    fun name() = suspendTest {
        val wasm = WastReader.parseModule(resourcesVfs["wasm-program.wast"].readString())
        for (func in wasm.functions) {
            val dump = BaseJavaExporter().dump(BaseJavaExporter.DumpContext(func), func.code2!!.body)
            println(func.name + " : " + dump.indenter)
        }
    }
}