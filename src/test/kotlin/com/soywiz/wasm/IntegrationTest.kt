package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.*
import kotlin.test.*

class IntegrationTest {
    @Test
    fun testSimpleIntegrationTest() {
        assertExecutionEquals(
            "hello world\n0,1,2,3,4,5,6,7,8,9,\n",
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

    private fun compileAndExecute(source: String): String {
        var result = ""
        runBlocking {
            //val root = tempVfs
            val root = "/tmp".uniVfs
            root["wasm-program.c"].writeString(source)
            println(root.absolutePath)

            // C -> WASM
            val emccArgs = arrayOf("docker", "run", "--rm", "-v", "${root.absolutePath}:/src", "-t", "apiaryio/emcc", "emconfigure", "emcc", "wasm-program.c", "-o", "wasm-program", "-O3", "-s", "WASM=1")
            println(emccArgs.joinToString(" "))
            root.execToString(*emccArgs)

            // WASM -> Java
            val data = root["wasm-program.wasm"].readAll().openSync()
            root["Module.java"].writeString(Wasm.readAndConvert(data, "java").toString())

            // Java -> Class
            val javacArgs = arrayOf("docker", "run", "-v", "${root.absolutePath}:/src", "jboss/base-jdk:8", "javac", "/src/Module.java")
            println(javacArgs.joinToString(" "))
            root.execToString(*javacArgs)

            // Execute Java
            val javaArgs = arrayOf("docker", "run", "-v", "/tmp:/src", "jboss/base-jdk:8", "java", "-cp", "/src", "Module")
            println(javaArgs.joinToString(" "))
            result = root.execToString(*javaArgs)
        }
        return result
    }

    private fun assertExecutionEquals(expected: String, source: String) {
        assertEquals(expected, compileAndExecute(source), "Executing $source")
    }
}