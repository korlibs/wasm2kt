package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.vfs.*
import com.soywiz.wasm.exporter.*
import org.junit.*
import java.io.*

class WastTest {
    @Test
    fun name() = suspendTest {
        //val wasm = WastReader().parseModule(resourcesVfs["wasm-program.wast"].readString())
        //val wasm = WastReader().parseModule(resourcesVfs["wasm-program-hello-world2.wast"].readString())
        //val wasm = WastReader().parseModule(resourcesVfs["wasm-program-hello-world2-unoptimized.wast"].readString())
        val wasm = WastReader().parseModule(resourcesVfs["wasm-program-doubles-unoptimized.wast"].readString())
        val exporter = JavaExporter(wasm)
        File("/tmp/my/Module.java").apply { parentFile.mkdirs() }.writeText(exporter.dump(
            ExportConfig(
                className = "Module"
            )
        ).toString())

    }
}