package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.*
import com.soywiz.wasm.exporter.*
import kotlin.test.*

class IntegrationTest : BaseIntegrationTest() {
    @Test
    fun testSimple() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            int main() {
                printf("hello world\n");
                for (int n = 0; n < 10; n++) printf("%d,", n);
                printf("\n");
                return 0;
            }
            """,
            optimization = 0
            //optimization = 3
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

    @Test
    @Ignore
    fun testNanoSvg() {
        runBlocking {
            localCurrentDirVfs["samples/nanosvg"].copyToTree(root)
        }
        assertGccAndJavaExecutionAreEquals(
            """
            #include "example2.c"
            """,
            optimization = 0,
            wast = true
        )
    }

    @Test
    @Ignore
    fun testMalloc() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>

            int main() {
                unsigned char* data = (unsigned char *)malloc(1024);
                int sum = 0;
                unsigned long long int mul = 1;
                for (int n = 0; n < 1024; n++) data[n] = (unsigned char)n;
                for (int n = 0; n < 1024; n++) if (data[n] > 7) sum += data[n];
                for (int n = 0; n < 64; n++) if (data[n] != 0) mul *= data[n];
                printf("%d, %llu\n", sum, mul);
                free(data);
                return 0;
            }
            """
        )
    }

    @Test
    fun testDoubles() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>

            double toDouble(const char *str) { return atof(str); }
            float toFloat(const char *str) { return (float)atof(str); }
            int toInt(const char *str) { return (int)atof(str); }

            int main() {
                printf("%d\n", toInt("123.125"));
                printf("%d\n", (int)toFloat("123.125"));
                printf("%f\n", toFloat("123.125"));
                //printf("%lf\n", toDouble("123.125"));
                return 0;
            }
            """,
            optimization = 0,
            wast = true
        )
    }

    @Test
    fun testFiles() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>
            int main() {
                FILE *filew = fopen("fopen-wasm", "wb");
                fprintf(filew, "%s %s\n", "hello", "world");
                fclose(filew);

                FILE *filer = fopen("fopen-wasm", "rb");
                printf("%d\n", (int)fseek(filer, 0, SEEK_END));
                printf("%d\n", (int)ftell(filer));
                printf("%d\n", (int)fseek(filer, -8, SEEK_CUR));
                printf("%d\n", (int)ftell(filer));
                char data[64] = {0};
                int read = fread(data, 1, sizeof(data), filer);
                printf("%d\n", read);
                data[read] = 0;
                fclose(filer);

                printf("'%s'\n", data);
                //fwrite(data, 1, strlen(data), stdout);

                return 0;
            }
            """,
            optimization = 0,
            wast = true,
            cleanup = {
                root["fopen-wasm"].delete()
            }
        )
    }

    @Test
    fun testArgs() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>

            int main(int args, char** argv) {
                for (int n = 1; n < args; n++) {
                    printf("%s\n", argv[n]);
                }
                return 0;
            }
            """,
            optimization = 0,
            wast = true,
            args = *arrayOf("a", "bc", "def")
        )
    }
}

@Suppress("MemberVisibilityCanBePrivate")
open class BaseIntegrationTest {

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

    protected suspend fun runCommand(vararg args: String, passthru: Boolean = false): String {
        println(args.joinToString(" "))
        val startTime = System.currentTimeMillis()
        val result = if (passthru) {
            root.passthru(*args)
            ""
        } else {
            root.execToString(*args)
        }
        val endTime = System.currentTimeMillis()
        println(" ---> ${endTime - startTime}")
        return result
    }

    protected fun compileAndExecuteGCC(source: String, vararg args: String): String {
        var result = ""
        runBlocking {
            root["wasm-program.c"].writeString(source)
            root["wasm-program.out"].delete()

            // C -> BIN -> Execute Native
            val argsStr = args.joinToString(" ") { it }
            result = runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", GCC_IMAGE,
                "/bin/sh", "-c", "gcc /src/wasm-program.c -o /src/wasm-program.out && /src/wasm-program.out $argsStr"
            )
        }
        return result
    }

    protected fun compileAndExecuteJava(source: String, vararg args: String, optimization: Int = 3, wast: Boolean = false): String {
        var result = ""
        runBlocking {
            root["wasm-program.c"].writeString(source)
            root["wasm-program.wasm"].delete()
            root["Module.java"].delete()
            root["Module.class"].delete()

            // C -> WASM
            runCommand(
                "docker", "run", "--rm", "-v", "${root.absolutePath}:/src", "-t", EMCC_IMAGE,
                "emconfigure", "emcc", "wasm-program.c", "-o", "wasm-program",
                "-O$optimization", "-g4", "-s", "WASM=1",
                //"-O$optimization", if (optimization == 0) "-g4" else "-g0", "-s", "WASM=1",
                passthru = true
            )

            // WASM -> Java
            val wasm = when (wast) {
                true -> WastReader().parseModule(root["wasm-program.wast"].readString())
                false -> Wasm.read(root["wasm-program.wasm"].readAll().openSync())
            }
            root["Module.java"].writeString(
                JavaExporter(wasm).dump(
                    ExportConfig(
                        className = "Module"
                    )
                ).toString())

            // Java -> Class -> Execute
            val argsStr = args.joinToString(" ") { it }
            result = runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", JDK_IMAGE,
                "/bin/sh", "-c", "cd /src && javac /src/Module.java && java -cp /src Module $argsStr"
            )
        }
        return result
    }

    protected fun assertGccAndJavaExecutionAreEquals(source: String, vararg args: String, optimization: Int = 3, wast: Boolean = false, cleanup: suspend () -> Unit = {}) {
        runBlocking { cleanup() }
        val javaOutput = compileAndExecuteJava(source, *args, optimization = optimization, wast = wast)

        runBlocking { cleanup() }
        val gccOutput = compileAndExecuteGCC(source, *args)
        runBlocking { cleanup() }

        println(gccOutput)
        assertEquals(gccOutput, javaOutput, "Executing args=${args.toList()}\n" + source.trimIndent())
    }

    //private fun assertExecutionEquals(expected: String, source: String) {
    //    assertEquals(expected, compileAndExecuteJava(source), "Executing $source")
    //}
}