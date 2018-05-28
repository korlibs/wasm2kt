package com.soywiz.wasm.exporter

import com.soywiz.korio.error.*
import com.soywiz.korio.util.*
import com.soywiz.wasm.*
import com.soywiz.wasm.util.*
import com.soywiz.wasm.util.Indenter

open class KotlinJvmExporter(module: WasmModule) : JvmExporter(module) {
    override fun langNameAllocator() = NameAllocator {
        //var res = it.replace('$', '_').replace('-', '_')
        //var res = "`" + it.replace('-', '_') + "`"
        var res = "k" + it.replace('-', '_').replace('$', '_')
        while (res in DEFINED_NAMES) res = "_$res"
        res
    }

    override val AND = "and"
    override val OR = "or"
    override val XOR = "xor"
    override val SHL = "shl"
    override val SHR = "shr"
    override val USHR = "ushr"

    override fun ternary(cond: String, strue: String, sfalse: String) = "(if ($cond) $strue else $sfalse)"
    override fun cast(type: WasmType, expr: String) = "(($expr).to${type.type()}())"

    override fun writeSetLocal(localName: String, expr: String) =
        Indenter { line("$localName = $expr") }
    override fun writeSetGlobal(globalName: String, expr: String) =
        Indenter { line("this.$globalName = $expr") }

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
        line("val UTF_8: java.nio.charset.Charset = java.nio.charset.Charset.forName(\"UTF-8\")")
        line("const val HEAP_SIZE = 64 * 1024 * 1024 // 64 MB")
        line("const val STACK_INITIAL_SIZE = 128 * 1024 // 128 KB ")
    }

    override fun Indenter.memoryDynamics() {
        line("val heapBytes = ByteArray(HEAP_SIZE)")
        line("val heap = java.nio.ByteBuffer.wrap(heapBytes).order(java.nio.ByteOrder.nativeOrder())")
    }

    override val String.asize get() = "$this.size"

    override fun Indenter.FUNC_INLINE_RAW(name: String, ret: WasmType, vararg args: MyArg, pub: Boolean, body: () -> String) {
        val s = if (pub) "" else "private "
        line("${s}fun $name(${args.argstr()}): ${ret.type()} { ${body()} }")
    }

    override fun Indenter.FUNC_INDENTER(name: String, ret: WasmType, vararg args: MyArg, pub: Boolean, body: Indenter.() -> Unit) {
        val s = if (pub) "" else "private "
        line("${s}fun $name(${args.argstr()}): ${ret.type()}") {
            body()
        }
    }

    override fun Indenter.SWITCH(expr: String, callback: Indenter.() -> Unit) {
        line("when ($expr)") {
            callback()
        }
    }

    override fun Indenter.CASE_RETURN(rettype: WasmType, index: Int, expr: String) {
        line("$index -> return $expr")
    }

    override fun Indenter.CASE_STM_BREAK(index: Int, callback: Indenter.() -> Unit) {
        line("$index -> ") {
            callback()
        }
    }

    override fun Indenter.CASE_FSTM(index: Int, callback: Indenter.() -> Unit) {
        line("$index ->")
        indent {
            callback()
        }
    }

    override fun Indenter.CASE_DEFAULT_FSTM(callback: Indenter.() -> Unit) {
        line("else ->")
        indent {
            callback()
        }
    }

    override fun Array<out MyArg>.argstr() = this.joinToString(", ") {
        val out = if (it.type is WasmType._VARARG) "vararg " else ""
        "$out${it.name}: ${it.type.type()}"
    }

    override fun WasmType.type(instantiate: Boolean): String = when (this) {
        WasmType._boolean -> "Boolean"
        WasmType.void -> "Unit"
        WasmType._i8 -> "Byte"
        WasmType._i16 -> "Short"
        WasmType.i32 -> "Int"
        WasmType.i64 -> "Long"
        WasmType.f32 -> "Float"
        WasmType.f64 -> "Double"
        is WasmType._ARRAY -> "${this.element.type(instantiate)}Array"
        is WasmType._VARARG -> this.element.type(instantiate)
        is WasmType._NULLABLE -> if (instantiate) this.element.type(instantiate) else this.element.type(instantiate) + "?"
        is WasmType.Function -> "(${this.args.joinToString(", ") { it.type.type(instantiate) }}) -> ${this.retType.type(instantiate)}"
        else -> "$this"
    }

    override fun Indenter.THROW(expr: String) {
        line("throw $expr")
    }

    override fun Indenter.CATCH(arg: MyArg, callback: Indenter.() -> Unit) {
        line("catch (${arg.name}: ${arg.type.type()})") {
            callback()
        }
    }

    override fun NEW(type: WasmType, vararg args: String): String {
        return "${type.type(instantiate = true)}(${args.joinToString(", ")})"
    }

    override fun Indenter.LOCAL(arg: MyArg, expr: String) {
        line("var ${arg.name}: ${arg.type.type()} = $expr")
    }

    override fun Indenter.FIELD(type: WasmType, name: String, expr: String) {
        line("var $name: ${type.type()} = $expr")
    }

    override fun Indenter.FOR(v: String, from: String, until: String, body: Indenter.() -> Unit) {
        line("for ($v in $from until $until)") {
            body()
        }
    }

    override fun STM_EXPR_INLINE(expr: String): String = expr
    override fun RETURN_INLINE(expr: String) = "return $expr"

    override val IntegerType = "Int"

    override fun Indenter.RETURN_VOID() {
        line("return")
    }

    override val String.cinv get() = "(($this).inv())"

    override fun String.stringGetBytes(charset: String) = "$this.toByteArray($charset)"

    override fun Indenter.LABEL(name: String) {
        line("$name@")
    }

    override fun AstLabel.goto(ctx: DumpContext) = "${this.kind.keyword}@${ctx.getName(this)}"

    override fun Indenter.BREAK(name: String?) {
        line(if (name.isNullOrEmpty()) "break" else "break@$name")
    }

    override fun Indenter.CONTINUE(name: String?) {
        line(if (name.isNullOrEmpty()) "continue" else "continue@$name;")
    }

    override fun Indenter.BLOCK(body: Indenter.() -> Unit) {
        line("do") {
            body()
        }
        line("while (false)")
    }

    override fun Indenter.dumpExtraLocals(ctx: DumpContext, func: WasmFunc, locs: List<AstLocal>, args: Set<AstLocal>) {
        for (local in locs.filter { it in args }) {
            LOCAL(local.type(ctx.getName(local)), ctx.getName(local))
        }
    }

    override fun const(value: Long) = when (value) {
        java.lang.Long.MIN_VALUE -> "java.lang.Long.MIN_VALUE" // this causes an error in kotlin!
        java.lang.Long.MAX_VALUE -> "java.lang.Long.MAX_VALUE"
        else -> "${value}L"
    }

    override fun Indenter.preClass() {
        line("@Suppress(${supresses.joinToString(", ") { it.quoted }})")
    }

    override fun System_in() = "System.`in`"
}
