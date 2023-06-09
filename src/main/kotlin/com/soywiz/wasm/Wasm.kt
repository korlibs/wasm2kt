package com.soywiz.wasm

import com.soywiz.wasm.exporter.*
import com.soywiz.wasm.util.Indenter
import korlibs.io.Korio
import korlibs.io.file.extensionLC
import korlibs.io.file.std.uniVfs
import korlibs.io.lang.readString
import korlibs.io.stream.SyncStream
import korlibs.io.stream.openSync
import java.util.*

object Wasm {
    @JvmStatic
    fun main(args: Array<String>) = Korio {
        val margs = LinkedList<String>(args.toList())
        var showHelp = false
        //var lang: String = "kotlin"
        var lang: String = "java"
        var className = "Module"
        var packageName = ""
        var file: String? = null
        if (margs.isEmpty()) showHelp = true
        while (margs.isNotEmpty()) {
            val arg = margs.removeFirst()
            if (arg.startsWith("-")) {
                when (arg) {
                    "-lang" -> {
                        lang = margs.removeFirst()
                    }
                    "-class" -> {
                        className = margs.removeFirst()
                    }
                    "-package" -> {
                        packageName = margs.removeFirst()
                    }
                    "-h", "-help", "--help", "-?" -> {
                        showHelp = true
                    }
                }
            } else {
                file = arg
            }
        }
        if (showHelp || file == null) {
            System.err.println("wasm2kt [-lang java|kotlin|kotlin-common] [-class Module] [-package my.java.package] <file.wasm|file.wast>")
            System.exit(0)
            error("unreachable")
        }
        val fileContents = file.uniVfs.readAll()

        val module = when {
            fileContents.readString(0, 4) == "\u0000asm" ->
                read(fileContents.openSync())
            (fileContents.readString(0, 4) == "(mod") || (file.uniVfs.extensionLC == "wast") ->
                WastReader().parseModule(fileContents.toString(Charsets.UTF_8))
            else -> TODO("Not a WASM or WAST file")
        }
        val exporter = exporter(lang, module)
        println(exporter.dump(ExportConfig(className = className, packageName = packageName)))
    }

    fun exporter(lang: String, wasm: WasmModule): Exporter {
        val exporter = when (lang) {
            "kotlin", "kotlin-jvm" -> KotlinJvmExporter(wasm)
            "kotlin-common" -> KotlinCommonExporter(wasm)
            "java" -> JavaExporter(wasm)
            else -> error("Unsupported exporter '$lang'. Only supported 'java' and 'kotlin'.")
        }
        return exporter
    }

    fun read(s: SyncStream): WasmModule {
        val wasm = WasmReader()
        wasm.read(s)
        return wasm.toModule()
    }

    fun readAndConvert(s: SyncStream, lang: String, className: String = "Module"): Indenter {
        val wasm = WasmReader()
        wasm.read(s)
        return exporter(lang, wasm.toModule()).dump(ExportConfig(className))
    }
}


