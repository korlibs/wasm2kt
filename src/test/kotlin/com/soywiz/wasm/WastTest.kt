package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.vfs.*
import org.junit.*

class WastTest {
    @Test
    fun name() = suspendTest {
        Wast.parseModule(resourcesVfs["wasm-program.wast"].readString())
    }
}