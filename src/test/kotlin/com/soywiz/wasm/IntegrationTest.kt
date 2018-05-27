package com.soywiz.wasm

import com.soywiz.korio.async.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.*
import com.soywiz.korio.vfs.*
import com.soywiz.wasm.exporter.*
import java.security.*
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
    fun testArithmetic() {
        assertGccAndJavaExecutionAreEquals(
            """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>
            #include <math.h>

            typedef int i32;
            typedef unsigned int u32;
            typedef long long int i64;
            typedef unsigned long long int u64;
            typedef float f32;
            typedef double f64;

            static i32 int_values1[] = {-2147483648, -1000, -7, -2, -1, 0, +1, +2, +7, +1000, 2147483647};
            static i32 int_values2[] = {-1000, -7, -2, -1, +1, +2, +7, +1000};

            static u32 u32_values1[] = {0, +1, +2, +7, +1000, 2147483647, 2147483648, 0xFFFFFFFF};
            static u32 u32_values2[] = {+1, +2, +7, +1000, +999999};

            static i64 long_values1[] = {-9223372036854775808LL, -2147483648, -1000, -7, -2, -1, 0, +1, +2, +7, +1000, 2147483647, 9223372036854775807LL};
            static i64 long_values2[] = {-1000, -7, -2, -1, +1, +2, +7, +1000};

            static u64 u64_values1[] = {0, +1, +2, +7, +1000, 2147483647, 9223372036854775807LL, 0xFFFFFFFFFFFFFFFFL};
            static u64 u64_values2[] = {+1, +2, +7, +1000};

            static f32 float_values1[] = {-2147483648.f, -1000.f, -7.f, -2.f, -1.f, 0.f, 1.f, 2.f, 7.f, 1000.f, 2147483647.f};
            static f32 float_values2[] = {-1000.f, -7.f, -2.f, -1.f, 1.f, 2.f, 7.f, 1000.f};

            static f64 double_values1[] = {-2147483648.0, -1000.0, -7.0, -2.0, -1.0, 0.0, 1.0, 2.0, 7.0, 1000.0, 2147483647.0};
            static f64 double_values2[] = {-1000.0, -7.0, -2.0, -1.0, 1.0, 2.0, 7.0, 1000.0};

            #define lengthof(V) (sizeof((V)) / sizeof((V)[0]))
            #define FOR_INT(VEC, N, V) for (i32 N = 0, V = VEC[N]; N < lengthof(VEC); V = VEC[N++])
            #define FOR_U32(VEC, N, V) for (u32 N = 0, V = VEC[N]; N < lengthof(VEC); V = VEC[N++])
            #define FOR_LONG(VEC, N, V) for (i64 N = 0, V = VEC[N]; N < lengthof(VEC); V = VEC[N++])
            #define FOR_U64(VEC, N, V) for (u64 N = 0, V = VEC[N]; N < lengthof(VEC); V = VEC[N++])
            #define FOR_FLOAT(VEC, N, V) for (f32 N = 0, V = VEC[(int)N]; N < lengthof(VEC); V = VEC[(int)(N++)])
            #define FOR_DOUBLE(VEC, N, V) for (f64 N = 0, V = VEC[(int)N]; N < lengthof(VEC); V = VEC[(int)(N++)])

            #define INT_UNOP(VEC, OP) printf("%s", "INT[" #OP "]:" ); FOR_INT(VEC, n, v) printf("%d,", OP); printf("\n");
            #define INT_BINOP(VEC, OP) printf("%s", "INT[" #OP "]:" ); FOR_INT(VEC, n, l) FOR_INT(VEC, m, r) printf("%d,", OP); printf("\n");
            #define INT_BINOP_bool(VEC, OP) printf("%s", "INT[" #OP "]:" ); FOR_INT(VEC, n, l) FOR_INT(VEC, m, r) printf("%d,", (OP) ? 1 : 0); printf("\n");

            #define U32_UNOP(VEC, OP) printf("%s", "U32[" #OP "]:" ); FOR_U32(VEC, n, v) printf("%u,", OP); printf("\n");
            #define U32_BINOP(VEC, OP) printf("%s", "U32[" #OP "]:" ); FOR_U32(VEC, n, l) FOR_U32(VEC, m, r) printf("%u,", OP); printf("\n");
            #define U32_BINOP_bool(VEC, OP) printf("%s", "U32[" #OP "]:" ); FOR_U32(VEC, n, l) FOR_U32(VEC, m, r) printf("%u,", (OP) ? 1 : 0); printf("\n");

            #define LONG_UNOP(VEC, OP) printf("%s", "LONG[" #OP "]:" ); FOR_LONG(VEC, n, v) printf("%lld,", OP); printf("\n");
            #define LONG_BINOP(VEC, OP) printf("%s", "LONG[" #OP "]:" ); FOR_LONG(VEC, n, l) FOR_LONG(VEC, m, r) printf("%lld,", OP); printf("\n");
            #define LONG_BINOP_bool(VEC, OP) printf("%s", "LONG[" #OP "]:" ); FOR_LONG(VEC, n, l) FOR_LONG(VEC, m, r) printf("%d,", (OP) ? 1 : 0); printf("\n");

            #define U64_UNOP(VEC, OP) printf("%s", "U64[" #OP "]:" ); FOR_U64(VEC, n, v) printf("%llu,", OP); printf("\n");
            #define U64_BINOP(VEC, OP) printf("%s", "U64[" #OP "]:" ); FOR_U64(VEC, n, l) FOR_U64(VEC, m, r) printf("%llu,", OP); printf("\n");
            #define U64_BINOP_bool(VEC, OP) printf("%s", "U64[" #OP "]:" ); FOR_U64(VEC, n, l) FOR_U64(VEC, m, r) printf("%u,", (OP) ? 1 : 0); printf("\n");

            #define FLOAT_UNOP(VEC, OP) printf("%s", "FLOAT[" #OP "]:" ); FOR_FLOAT(VEC, n, v) printf("%f,", (double)(OP)); printf("\n");
            #define FLOAT_BINOP(VEC, OP) printf("%s", "FLOAT[" #OP "]:" ); FOR_FLOAT(VEC, n, l) FOR_FLOAT(VEC, m, r) printf("%f,", (double)(OP)); printf("\n");
            #define FLOAT_BINOP_bool(VEC, OP) printf("%s", "FLOAT[" #OP "]:" ); FOR_FLOAT(VEC, n, l) FOR_FLOAT(VEC, m, r) printf("%d,", (OP) ? 1 : 0); printf("\n");

            #define DOUBLE_UNOP(VEC, OP) printf("%s", "DOUBLE[" #OP "]:" ); FOR_DOUBLE(VEC, n, v) printf("%f,", (double)(OP)); printf("\n");
            #define DOUBLE_BINOP(VEC, OP) printf("%s", "DOUBLE[" #OP "]:" ); FOR_DOUBLE(VEC, n, l) FOR_DOUBLE(VEC, m, r) printf("%f,", (double)(OP)); printf("\n");
            #define DOUBLE_BINOP_bool(VEC, OP) printf("%s", "DOUBLE[" #OP "]:" ); FOR_DOUBLE(VEC, n, l) FOR_DOUBLE(VEC, m, r) printf("%d,", (OP) ? 1 : 0); printf("\n");

            int main() {
                int* data = malloc(sizeof(int) * 1024);
                { for (int n = 0; n < 1024; n++) data[n] = n * 3; }

                INT_UNOP (int_values1, v)
                INT_UNOP (int_values1, ~v)
                INT_UNOP (int_values1, -v)
                INT_BINOP(int_values1, l + r)
                INT_BINOP(int_values1, l - r)
                INT_BINOP(int_values1, l * r)
                INT_BINOP(int_values2, l / r)
                INT_BINOP(int_values2, l % r)
                INT_BINOP(int_values1, l & r)
                INT_BINOP(int_values1, l ^ r)
                INT_BINOP(int_values1, l | r)
                INT_BINOP(int_values1, l << r)
                INT_BINOP(int_values1, l >> r)
                INT_BINOP_bool(int_values1, l == r)
                INT_BINOP_bool(int_values1, l != r)
                INT_BINOP_bool(int_values1, l < r)
                INT_BINOP_bool(int_values1, l > r)
                INT_BINOP_bool(int_values1, l <= r)
                INT_BINOP_bool(int_values1, l >= r)

                U32_UNOP (u32_values1, v)
                U32_UNOP (u32_values1, ~v)
                U32_UNOP (u32_values1, -v)
                U32_BINOP(u32_values1, l + r)
                U32_BINOP(u32_values1, l - r)
                U32_BINOP(u32_values1, l * r)
                U32_BINOP(u32_values2, l / r)
                U32_BINOP(u32_values2, l % r)
                U32_BINOP(u32_values1, l & r)
                U32_BINOP(u32_values1, l ^ r)
                U32_BINOP(u32_values1, l | r)
                U32_BINOP(u32_values1, l << r)
                U32_BINOP(u32_values1, l >> r)
                U32_BINOP_bool(u32_values1, l == r)
                U32_BINOP_bool(u32_values1, l != r)
                U32_BINOP_bool(u32_values1, l < r)
                U32_BINOP_bool(u32_values1, l > r)
                U32_BINOP_bool(u32_values1, l <= r)
                U32_BINOP_bool(u32_values1, l >= r)

                LONG_UNOP (long_values1, v)
                LONG_UNOP (long_values1, ~v)
                LONG_UNOP (long_values1, -v)
                LONG_BINOP(long_values1, l + r)
                LONG_BINOP(long_values1, l - r)
                LONG_BINOP(long_values1, l * r)
                LONG_BINOP(long_values2, l / r)
                LONG_BINOP(long_values2, l % r)
                LONG_BINOP(long_values1, l & r)
                LONG_BINOP(long_values1, l ^ r)
                LONG_BINOP(long_values1, l | r)
                LONG_BINOP(long_values1, l << r)
                LONG_BINOP(long_values1, l >> r)
                LONG_BINOP_bool(long_values1, l == r)
                LONG_BINOP_bool(long_values1, l != r)
                LONG_BINOP_bool(long_values1, l < r)
                LONG_BINOP_bool(long_values1, l > r)
                LONG_BINOP_bool(long_values1, l <= r)
                LONG_BINOP_bool(long_values1, l >= r)

                U64_UNOP (u64_values1, v)
                U64_UNOP (u64_values1, ~v)
                U64_UNOP (u64_values1, -v)
                U64_BINOP(u64_values1, l + r)
                U64_BINOP(u64_values1, l - r)
                U64_BINOP(u64_values1, l * r)
                U64_BINOP(u64_values2, l / r)
                U64_BINOP(u64_values2, l % r)
                U64_BINOP(u64_values1, l & r)
                U64_BINOP(u64_values1, l ^ r)
                U64_BINOP(u64_values1, l | r)
                U64_BINOP(u64_values1, l << r)
                U64_BINOP(u64_values1, l >> r)
                U64_BINOP_bool(u64_values1, l == r)
                U64_BINOP_bool(u64_values1, l != r)
                U64_BINOP_bool(u64_values1, l < r)
                U64_BINOP_bool(u64_values1, l > r)
                U64_BINOP_bool(u64_values1, l <= r)
                U64_BINOP_bool(u64_values1, l >= r)

                FLOAT_UNOP (float_values1, v)
                FLOAT_UNOP (float_values1, -v)
                //FLOAT_UNOP (float_values1, fabs(v))
                FLOAT_BINOP(float_values1, l + r)
                FLOAT_BINOP(float_values1, l - r)
                FLOAT_BINOP(float_values1, l * r)
                FLOAT_BINOP(float_values2, l / r)
                //FLOAT_BINOP(float_values2, l % r)
                FLOAT_BINOP_bool(float_values1, l == r)
                FLOAT_BINOP_bool(float_values1, l != r)
                FLOAT_BINOP_bool(float_values1, l < r)
                FLOAT_BINOP_bool(float_values1, l > r)
                FLOAT_BINOP_bool(float_values1, l <= r)
                FLOAT_BINOP_bool(float_values1, l >= r)

                DOUBLE_UNOP (double_values1, v)
                DOUBLE_UNOP (double_values1, -v)
                //DOUBLE_UNOP (double_values1, abs(v)) // BLOCK_EXPR
                DOUBLE_BINOP(double_values1, l + r)
                DOUBLE_BINOP(double_values1, l - r)
                DOUBLE_BINOP(double_values1, l * r)
                DOUBLE_BINOP(double_values2, l / r)
                //DOUBLE_BINOP(double_values2, l % r)
                DOUBLE_BINOP_bool(double_values1, l == r)
                DOUBLE_BINOP_bool(double_values1, l != r)
                DOUBLE_BINOP_bool(double_values1, l < r)
                DOUBLE_BINOP_bool(double_values1, l > r)
                DOUBLE_BINOP_bool(double_values1, l <= r)
                DOUBLE_BINOP_bool(double_values1, l >= r)

                printf("DONE\n");
                return 0;
            }
            """,
            optimization = 0,
            wast = true
        )
    }

    @Test
    fun testNanoSvg() {
        runBlocking {
            localCurrentDirVfs["samples/nanosvg"].copyToTree(root)
        }
        val svgPng = root["svg.png"]
        val checkOutput = suspend {
            assertEquals("2bb27a0d118712a2894ff56e80fd7dee", svgPng.readAll().md5a().hex)
        }
        assertGccAndJavaExecutionAreEquals(
            """
            #include "example2.c"
            """,
            optimization = 0,
            wast = true,
            cleanup = { svgPng.delete() },
            checkAfterJava = { checkOutput() },
            checkAfterGcc = { checkOutput() }
        )
    }

    fun ByteArray.md5a() = MessageDigest.getInstance("MD5").digest(this)

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
                "/bin/sh", "-c", "cd /src && gcc /src/wasm-program.c -o /src/wasm-program.out && /src/wasm-program.out $argsStr"
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
            val classStr = JavaExporter(wasm).dump(ExportConfig(className = "Module")).toString()
            root["Module.java"].writeString(classStr)
            root["Module2.java"].writeString(classStr)

            // Java -> Class -> Execute
            val argsStr = args.joinToString(" ") { it }
            result = runCommand(
                "docker", "run", "-v", "${root.absolutePath}:/src", JDK_IMAGE,
                "/bin/sh", "-c", "cd /src && javac /src/Module.java && java -cp /src Module $argsStr"
            )
        }
        return result
    }

    protected fun assertGccAndJavaExecutionAreEquals(
        source: String,
        vararg args: String,
        optimization: Int = 3, wast: Boolean = false,
        cleanup: suspend () -> Unit = {},
        checkAfterJava: suspend () -> Unit = {},
        checkAfterGcc: suspend () -> Unit = {}
    ) {
        runBlocking { cleanup() }
        val javaOutput = compileAndExecuteJava(source, *args, optimization = optimization, wast = wast)
        runBlocking { checkAfterJava() }
        runBlocking { cleanup() }
        val gccOutput = compileAndExecuteGCC(source, *args)
        runBlocking { checkAfterGcc() }
        runBlocking { cleanup() }

        println(gccOutput)
        assertEquals(gccOutput, javaOutput, "Executing args=${args.toList()}\n" + source.trimIndent())
    }

    //private fun assertExecutionEquals(expected: String, source: String) {
    //    assertEquals(expected, compileAndExecuteJava(source), "Executing $source")
    //}
}