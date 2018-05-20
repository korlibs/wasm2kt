package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.*
import kotlin.test.*

class IntegrationTest {
    @Test
    fun testSimpleIntegrationTest() {
        assertGccAndJavaExecutionAreEquals(
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

    @Test
    fun testTime() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>
            int main() {
                time_t result3 = {0};
                time_t result1 = time(NULL);
                time_t result2 = time(&result3);
                printf("%d,%d,%d\n", ((int)result1) / 100000, ((int)result2) / 100000, ((int)result3) / 100000);
                return 0;
            }
            """
        )
    }

    //val root = tempVfs
    val root = "/tmp".uniVfs
    //val JDK_IMAGE = "jboss/base-jdk:8"
    //val GCC_IMAGE = "gcc:8.1.0"
    //val EMCC_IMAGE = "apiaryio/emcc"
    //val EMCC_IMAGE = "apiaryio/emcc:1.34"
    val JDK_IMAGE = "openjdk:8-jdk-alpine3.7"
    val GCC_IMAGE = "frolvlad/alpine-gcc:latest"
    val EMCC_IMAGE = "apiaryio/emcc:1.37"

    init {
        println("ROOT: " + root.absolutePath)
    }

    private suspend fun runCommand(vararg args: String, passthru: Boolean = false): String {
        println(args.joinToString(" "))
        if (passthru){
            root.passthru(*args)
            return ""
        } else {
            return root.execToString(*args)
        }
    }

    private fun compileAndExecuteGCC(source: String, vararg args: String): String {
        var result = ""
        runBlocking {
            root["wasm-program.c"].writeString(source)
            root["wasm-program.out"].delete()

            // C -> BIN
            runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", GCC_IMAGE,
                "gcc", "/src/wasm-program.c", "-o", "/src/wasm-program.out",
                passthru = true
            )

            // Execute Native
            result = runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", GCC_IMAGE,
                "/src/wasm-program.out", *args
            )
        }
        return result
    }

    private fun compileAndExecuteJava(source: String, vararg args: String): String {
        var result = ""
        runBlocking {
            root["wasm-program.c"].writeString(source)
            root["wasm-program.wasm"].delete()
            root["Module.java"].delete()
            root["Module.class"].delete()

            // C -> WASM
            runCommand(
                "docker", "run", "--rm", "-v", "${root.absolutePath}:/src", "-t", EMCC_IMAGE,
                "emconfigure", "emcc", "wasm-program.c", "-o", "wasm-program", "-O3", "-s", "WASM=1",
                passthru = true
            )

            // WASM -> Java
            val data = root["wasm-program.wasm"].readAll().openSync()
            root["Module.java"].writeString(Wasm.readAndConvert(data, "java").toString())

            // Java -> Class
            runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", JDK_IMAGE,
                "javac", "/src/Module.java",
                passthru = true
            )

            // Execute Java
            result = runCommand(
                "docker", "run", "-v", "/tmp:/src", JDK_IMAGE,
                "java", "-cp", "/src", "Module", *args
            )
        }
        return result
    }

    private fun assertGccAndJavaExecutionAreEquals(source: String, vararg args: String) {
        val gccOutput = compileAndExecuteGCC(source, *args)
        val javaOutput = compileAndExecuteJava(source, *args)
        println(gccOutput)
        assertEquals(gccOutput, javaOutput, "Executing args=${args.toList()}\n" + source.trimIndent())
    }

    //private fun assertExecutionEquals(expected: String, source: String) {
    //    assertEquals(expected, compileAndExecuteJava(source), "Executing $source")
    //}
}