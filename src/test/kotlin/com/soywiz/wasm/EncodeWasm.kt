package com.soywiz.wasm

import com.soywiz.korio.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.*
import java.io.*

object EncodeWasm {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val data = resourcesVfs["encode.wasm"].readAll().openSync()
        //val data = resourcesVfs["hello.wasm"].readAll().openSync()
        File("/tmp/Module.java").writeText(Wasm.readAndConvert(data, "java").toString())
    }
}
