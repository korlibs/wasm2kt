package com.soywiz.wasm.exporter

import com.soywiz.korio.error.*
import com.soywiz.wasm.*

class KotlinExporter(module: WasmModule) : Exporter(module) {
    override val AND = "and"
    override val OR = "or"
    override val XOR = "xor"
    override val SHL = "shl"
    override val SHR = "shr"
    override val SHRU = "shru"

    override fun writeSetLocal(localName: String, expr: String) = Indenter { line("$localName = $expr") }
    override fun writeSetGlobal(globalName: String, expr: String) = Indenter { line("this.$globalName = $expr") }

    override fun Indenter.statics(callback: Indenter.() -> Unit) {
        line("companion object") {
            callback()
        }
    }

    override fun Indenter.dumpConstructor(className: String) {
        line("init") {
            for (nn in 0 until module.datas.size step initBlockSize) line("init$nn()")
            line("initDynamictop()")
        }
    }

    override fun Indenter.writeMain(className: String) {
        val mainFunc = module.functions.firstOrNull { it.export?.name == "_main" }
        if (mainFunc != null) {
            val funcName = moduleCtx.getName(mainFunc)
            line("@JvmStatic fun main(args: Array<String>)") {
                line("val module = $className()")
                line("val result: Int")
                //line("for (int m = 0; m < 100; m++)") {
                run {
                    val mainArity = mainFunc.type.args.size
                    when (mainArity) {
                        0 -> line("result = module.$funcName()")
                        2 -> {
                            line("val array = IntArray(args.size + 1);")
                            line("array[0] = module.allocStringz(\"$className\")")
                            FOR("n", "1", "array.size") {
                                line("array[n] = module.allocStringz(args[n - 1]);")
                            }
                            line("result = module.$funcName(array.size, module.allocInts(array));")
                        }
                        else -> invalidOp("Invalid function main with arity $mainArity")
                    }
                }
                line("System.exit(result)")
            }
        }
    }

    override fun Indenter.memoryStatics() {
        line("const val HEAP_SIZE = 64 * 1024 * 1024 // 64 MB")
        line("const val STACK_INITIAL_SIZE = 128 * 1024 // 128 KB ")
    }

    override fun Indenter.memoryDynamics() {
        line("val heapBytes = ByteArray(HEAP_SIZE)")
        line("val heap = java.nio.ByteBuffer.wrap(heapBytes).order(java.nio.ByteOrder.nativeOrder())")
    }

    override val String.asize get() = "$this.size"

    override fun Indenter.FUNC(name: String, ret: WasmType, vararg args: MyArg, pub: Boolean, body: () -> String) {
        val rbody = if (ret == void) body() else "return ${body()}"
        val s = if (pub) "" else "private "
        line("${s}fun $name(${args.argstr()}): ${ret.type()} { $rbody }")
    }

    override fun Array<out MyArg>.argstr() = this.joinToString(", ") { it.name + ": " + it.type.type() }

    override fun WasmType.type(): String = when (this) {
        WasmType.void -> "Unit"
        WasmType._i8 -> "Byte"
        WasmType._i16 -> "Short"
        WasmType.i32 -> "Int"
        WasmType.i64 -> "Long"
        WasmType.f32 -> "Float"
        WasmType.f64 -> "Double"
        is WasmType._ARRAY -> "${this.element.type()}Array"
        is WasmType.Function -> "(${this.args.joinToString(", ") { it.type.type() }}) -> ${this.retType.type()}"
        else -> "$this"
    }

    override fun Indenter.LOCAL(arg: MyArg, expr: String) {
        line("var ${arg.name}: ${arg.type.type()} = $expr")
    }

    override fun Indenter.FOR(v: String, from: String, until: String, body: Indenter.() -> Unit) {
        line("for ($v in $from until $until)") {
            body()
        }
    }

}
