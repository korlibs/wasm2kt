package com.soywiz.wasm

import korlibs.io.async.suspendTest
import korlibs.io.file.std.resourcesVfs
import korlibs.io.stream.openSync
import org.junit.Test

class WastParseTest {
    @Test
    fun test() = suspendTest {
        val data = Wasm.read(resourcesVfs["print010/wasm-program.wasm"].readAll().openSync())
        data.getFunction(0)
    }
}