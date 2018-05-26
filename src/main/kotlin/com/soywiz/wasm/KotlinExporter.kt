package com.soywiz.wasm

import com.soywiz.korio.error.*

/**
 * @TODO: Create a BaseExporter, so Java and Kotlin exporters can reuse code
 */
class KotlinExporter(val wasm: WasmModule) : Exporter {
    val module = wasm
    override fun dump(config: ExportConfig): Indenter = Indenter {
        //line("@Suppress(\"UNCHECKED_CAST\")")
        //line("@Suppress(\"UNCHECKED_CAST\", \"UNREACHABLE_CODE\")")
        line("@Suppress(\"UNCHECKED_CAST\", \"UNREACHABLE_CODE\", \"RedundantExplicitType\", \"UNUSED_VARIABLE\", \"VARIABLE_WITH_REDUNDANT_INITIALIZER\", \"CanBeVal\", \"RedundantUnitReturnType\", \"unused\", \"SelfAssignment\", \"ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE\", \"UNUSED_VALUE\", \"SelfAssignment\", \"LiftReturnOrAssignment\", \"LocalVariableName\", \"FunctionName\")")
        line("class Module : WasmModule()") {
            val mainFunc = module.functions.firstOrNull { it.name == "_main" }
            if (mainFunc != null) {
                line("companion object") {
                    line("@JvmStatic fun main(args: Array<String>)") {
                        when (mainFunc.type.args.size) {
                            0 -> line("Module()._main()")
                            1 -> line("Module()._main(0)")
                            2 -> line("Module()._main(0, 0)")
                        }

                    }
                }
            }
            val indices = LinkedHashMap<Int, String>()
            for (data in wasm.datas) {
                TODO()
                val ast = data.e!!.toAst(wasm, WasmFunc(-1, Wasm.INT_FUNC_TYPE))
                if (ast is Wast.RETURN && ast.expr is Wast.Const) {
                    indices[data.index] = "${ast.expr.value}"
                } else {
                    line("private fun computeDataIndex${data.index}(): Int") {
                        line(ast.dump())
                    }
                    indices[data.index] = "computeDataIndex${data.index}()"
                }
            }
            line("init") {
                for (data in wasm.datas) {
                    line("__putBytes(${indices[data.index]}, byteArrayOf(${data.data.joinToString(", ") { "$it" }}))")
                }
            }
            for (global in module.globals) {
                if (global.import == null) {
                    line("private fun compute${global.name}(): ${global.globalType.type.type()}") {
                        line(global.e!!.toAst(wasm, WasmFunc(-1, WasmType.Function(listOf(), listOf(global.globalType.type)))).dump())
                    }
                    line("var ${global.name}: ${global.globalType.type.type()} = compute${global.name}()")
                }
            }
            //val funcs = module.functions.values.joinToString(", ") { "this::${it.name}" }
            val funcs = module.elements.flatMap { it.funcRefs }.map { module.getFunction(it) ?: invalidOp("Invalid referenced function $it") }.joinToString(", ") { "this::${it.name}" }
            line("val functions = arrayOf($funcs)")

            for ((type, functions) in module.functions.groupBy { it.type }) {
                //val funcs = functions.joinToString(", ") { "this::${it.name}" }
                //line("val functions${type.signature} = arrayOf($funcs)")
                line("fun functions${type.signature}(index: Int) = functions[index] as ${type.type()}")
            }

            for (func in module.functions) {
                line("// func (${func.index}) : ${func.name}")
                line(dump(func))
            }
        }
    }

    fun dump(func: WasmFunc): Indenter = Indenter {
        val code = func.code
        if (code != null) {
            val body = code.body
            val bodyAst = body.toAst(wasm, func)
            val visibility = if (func.export != null) "" else "private "
            val args = func.type.args.withIndex().joinToString(", ") { "p" + it.index + ": ${it.value.type.type()}" }
            line("${visibility}fun ${func.name}($args): ${func.type.retType.type()}") {
                for ((index, local) in func.rlocals.withIndex()) {
                    val value = if (index < func.type.args.size) "p$index" else local.type.default()
                    line("var ${local.name}: ${local.type.type()} = $value")
                }
                for (local in bodyAst.getLocals()) {
                    if (local.index >= MIN_TEMP_VARIABLE) {
                        line("var ${local.name}: ${local.type.type()} = ${local.type.default()}")
                    }
                }
                line("var phi_i32: Int = 0")
                line("var phi_i64: Long = 0L")
                line("var phi_f32: Float = 0f")
                line("var phi_f64: Double = 0.0")
                line(bodyAst.dump())
            }
        }
    }

    fun Wast.Expr.dump(): String {
        return when (this) {
            is Wast.Const -> when (this.type) {
                WasmType.f32 -> "(${this.value}f)"
                WasmType.i64 -> "(${this.value}L)"
                else -> "(${this.value})"
            }
            is Wast.Local -> this.local.name
            is Wast.Global -> global.name
            is Wast.Unop -> {
                //"(" + " ${this.op.symbol} " + this.expr.dump() + ")"
                "$op(${this.expr.dump()})"
            }
            is Wast.Binop -> {
                val ld = l.dump()
                val rd = r.dump()
                //when (op){
                //    //Ops.Op_i32_add -> "($ld + $rd)"
                //    else -> "$op($ld, $rd)"
                //}
                "$op($ld, $rd)"
            }
            is Wast.CALL -> {
                val name = if (this.func.name.startsWith("___syscall")) "___syscall" else this.func.name
                "this.$name(${this.args.map { it.dump() }.joinToString(", ")})"
            }
            //is A.CALL_INDIRECT -> "((Op_getFunction(${this.address.dump()}) as (${this.type.type()}))" + "(" + this.args.map { it.dump() }.joinToString(
            is Wast.CALL_INDIRECT -> "(this.functions${this.type.signature}(${this.address.dump()})(" + this.args.map { it.dump() }.joinToString(
                ", "
            ) + "))"
            is Wast.ReadMemory -> {
                //this.access()
                "${this.op}(${this.address.dump()}, ${this.offset}, ${this.align})"
            }
            is Wast.Phi -> "phi_${this.type}"
            else -> "???($this)"
        }
    }

    fun WasmType.default(): String = when (this) {
        WasmType.i64 -> "0L"
        WasmType.f32 -> "0f"
        WasmType.f64 -> "0.0"
        else -> "0"
    }

    fun WasmType.type(): String = when (this) {
        WasmType.void -> "Unit"
        WasmType.i32 -> "Int"
        WasmType.i64 -> "Long"
        WasmType.f32 -> "Float"
        WasmType.f64 -> "Double"
        is WasmType.Function -> {
            "(${this.args.joinToString(", ") { it.type.type() }}) -> ${this.retType.type()}"
        }
        else -> "$this"
    }

    fun AstLabel.goto() = "${this.kind.keyword}@${this.name}"

    fun Wast.Stm.dump(out: Indenter = Indenter { }): Indenter {
        when (this) {
            is Wast.Stms -> {
                for (e in stms) e.dump(out)
            }
            is Wast.SetLocal -> out.line("${this.local.name} = ${this.expr.dump()}")
            is Wast.SetGlobal -> out.line("this.${this.global.name} = ${this.expr.dump()}")
            is Wast.RETURN -> out.line("return ${this.expr.dump()}")
            is Wast.RETURN_VOID -> out.line("return")
            is Wast.BLOCK -> {
                val optLabel = if (label != null) "${label.name}@" else ""
                out.line("${optLabel}do") {
                    this.stm.dump(out)
                }
                out.line("while (false)")
            }
            is Wast.LOOP -> {
                val optLabel = if (label != null) "${label.name}@" else ""
                out.line("${optLabel}while (true)") {
                    this.stm.dump(out)
                    out.line("break")
                }
            }
            is Wast.IF -> {
                out.line("if (${this.cond.dump()} != 0)") {
                    this.btrue.dump(out)
                }
            }
            is Wast.IF_ELSE -> {
                out.line("if (${this.cond.dump()} != 0)") {
                    this.btrue.dump(out)
                }
                out.line("else") {
                    this.bfalse.dump(out)
                }
            }
            is Wast.BR -> {
                out.line(this.label.goto())
            }
            is Wast.BR_IF -> {
                out.line("if (${this.cond.dump()} != 0) ${this.label.goto()}")
            }
            is Wast.BR_TABLE -> {
                out.line("when (${this.subject.dump()})") {
                    for ((index, label) in this.labels) {
                        out.line("$index -> ${label.goto()}")
                    }
                    out.line("else -> ${this.default.goto()}")
                }
            }
            is Wast.STM_EXPR -> {
                out.line(this.expr.dump())
            }
            is Wast.WriteMemory -> {
                //out.line("${this.access()} = ${this.value.dump()}")
                out.line("${this.op}(${this.address.dump()}, ${this.offset}, ${this.align}, ${this.value.dump()})")
            }
            is Wast.SetPhi -> {
                out.line("phi_${this.blockType} = ${this.value.dump()}")
            }
            is Wast.Unreachable -> {
                out.line("// Unreachable")
            }
            is Wast.NOP -> {
                out.line("// nop")
            }
            else -> out.line("??? $this")
        }
        return out
    }
}
