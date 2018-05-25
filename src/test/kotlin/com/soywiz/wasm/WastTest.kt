package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.vfs.*
import org.junit.*

class WastTest {
    @Test
    fun name() = suspendTest {
        val wasm = WastReader.parseModule(resourcesVfs["wasm-program.wast"].readString())
        val moduleContext = BaseJavaExporter.ModuleDumpContext()
        val exporter = JavaExporter(wasm)
        println(exporter.dump())
        /*
        for (func in wasm.functions) {
            val funcContext = BaseJavaExporter.DumpContext(moduleContext, func)
            val dump = BaseJavaExporter().dump(funcContext, func.code2!!.body)
            println(moduleContext.getName(func) + " : " + dump.indenter)
        }
        */
    }
}