package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.*
import kotlin.test.*

class IntegrationTest {
    //val root = tempVfs
    val root = "/tmp".uniVfs

    @Test
    fun testSimpleIntegrationTest() {
        assertGccJavaExecutionEquals(
            """
            #include <stdio.h>
            int main() {
                printf("hello world\n");
                for (int n = 0; n < 10; n++) printf("%d,", n);
                printf("\n");
                return 0;
            }
            """
        )
    }

    private suspend fun runCommand(vararg args: String): String {
        println(args.joinToString(" "))
        return root.execToString(*args)
    }

    private fun compileAndExecuteGCC(source: String): String {
        var result = ""
        runBlocking {
            root["wasm-program.c"].writeString(source)
            println(root.absolutePath)

            // C -> BIN
            runCommand(
                "docker",
                "run",
                "-v",
                "${root.absolutePath}:/src",
                "gcc:8.1.0",
                "gcc",
                "/src/wasm-program.c",
                "-o",
                "/src/wasm-program.out"
            )

            // Execute Native
            result = runCommand(
                "docker",
                "run",
                "-v",
                "${root.absolutePath}:/src",
                "gcc:8.1.0",
                "/src/wasm-program.out"
            )
        }
        return result
    }

    private fun compileAndExecuteJava(source: String): String {
        var result = ""
        runBlocking {
            //val root = tempVfs
            val root = "/tmp".uniVfs
            root["wasm-program.c"].writeString(source)
            println(root.absolutePath)

            // C -> WASM
            runCommand(
                "docker", "run", "--rm", "-v", "${root.absolutePath}:/src", "-t", "apiaryio/emcc",
                "emconfigure", "emcc", "wasm-program.c", "-o", "wasm-program", "-O3", "-s", "WASM=1"
            )

            // WASM -> Java
            val data = root["wasm-program.wasm"].readAll().openSync()
            root["Module.java"].writeString(Wasm.readAndConvert(data, "java").toString())

            // Java -> Class
            runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", "jboss/base-jdk:8",
                "javac", "/src/Module.java"
            )

            // Execute Java
            result = runCommand(
                "docker", "run", "-v", "/tmp:/src", "jboss/base-jdk:8",
                "java", "-cp", "/src", "Module"
            )
        }
        return result
    }

    private fun assertGccJavaExecutionEquals(source: String) {
        val gccOutput = compileAndExecuteGCC(source)
        val javaOutput = compileAndExecuteJava(source)
        println(gccOutput)
        assertEquals(gccOutput, javaOutput, "Executing $source")
    }

    //private fun assertExecutionEquals(expected: String, source: String) {
    //    assertEquals(expected, compileAndExecuteJava(source), "Executing $source")
    //}
}