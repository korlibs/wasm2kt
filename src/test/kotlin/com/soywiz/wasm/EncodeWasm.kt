package com.soywiz.wasm

import korlibs.io.Korio
import korlibs.io.file.std.resourcesVfs
import korlibs.io.stream.openSync
import java.io.*

object EncodeWasm {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val data = resourcesVfs["encode.wasm"].readAll().openSync()
        //val data = resourcesVfs["hello.wasm"].readAll().openSync()
        File("/tmp/Module.java").writeText(Wasm.readAndConvert(data, "java").toString())
    }
}
