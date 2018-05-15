package com.soywiz.wasm

import com.soywiz.korio.error.*

class KotlinExporter(val wasm: Wasm) : Exporter {
    val module = wasm
    override fun dump(): Indenter = Indenter {
        //line("@Suppress(\"UNCHECKED_CAST\")")
        //line("@Suppress(\"UNCHECKED_CAST\", \"UNREACHABLE_CODE\")")
        line("@Suppress(\"UNCHECKED_CAST\", \"UNREACHABLE_CODE\", \"RedundantExplicitType\", \"UNUSED_VARIABLE\", \"VARIABLE_WITH_REDUNDANT_INITIALIZER\", \"CanBeVal\", \"RedundantUnitReturnType\", \"unused\", \"SelfAssignment\", \"ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE\", \"UNUSED_VALUE\", \"SelfAssignment\", \"LiftReturnOrAssignment\", \"LocalVariableName\", \"FunctionName\")")
        line("class Module : WasmModule()") {
            val mainFunc = module.functions.values.firstOrNull { it.name == "_main" }
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
                val ast = data.e.toAst(wasm, Wasm.WasmFunc(wasm, -1, INT_FUNC_TYPE))
                if (ast is A.RETURN && ast.expr is A.Const) {
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
            for (global in module.globals.values) {
                if (global.import == null) {
                    line("private fun compute${global.name}(): ${global.globalType.type.type()}") {
                        line(global.e!!.toAst(wasm, Wasm.WasmFunc(wasm, -1, Wasm.WasmType.Function(listOf(), listOf(global.globalType.type)))).dump())
                    }
                    line("var ${global.name}: ${global.globalType.type.type()} = compute${global.name}()")
                }
            }
            //val funcs = module.functions.values.joinToString(", ") { "this::${it.name}" }
            val funcs = module.elements.flatMap { it.funcIdxs }.map { module.functions[it] ?: invalidOp("Invalid referenced function $it") }.joinToString(", ") { "this::${it.name}" }
            line("val functions = arrayOf($funcs)")

            for ((type, functions) in module.functions.values.groupBy { it.type }) {
                //val funcs = functions.joinToString(", ") { "this::${it.name}" }
                //line("val functions${type.signature} = arrayOf($funcs)")
                line("fun functions${type.signature}(index: Int) = functions[index] as ${type.type()}")
            }

            for (func in module.functions.values) {
                line("// func (${func.index}) : ${func.name}")
                line(dump(func))
            }
        }
    }

    fun dump(func: Wasm.WasmFunc): Indenter = Indenter {
        val code = func.code
        if (code != null) {
            val body = code.body
            val bodyAst = body.toAst(wasm, func)
            val visibility = if (func.export != null) "" else "private "
            val args = func.type.args.withIndex().joinToString(", ") { "p" + it.index + ": ${it.value.type()}" }
            line("${visibility}fun ${func.name}($args): ${func.type.retType.type()}") {
                for ((index, local) in func.rlocals.withIndex()) {
                    val value = if (index < func.type.args.size) "p$index" else local.default()
                    line("var l$index: ${local.type()} = $value")
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

    fun A.Expr.dump(): String {
        return when (this) {
            is A.Const -> when (this.type) {
                Wasm.WasmType.f32 -> "(${this.value}f)"
                Wasm.WasmType.i64 -> "(${this.value}L)"
                else -> "(${this.value})"
            }
            is A.Local -> this.local.name
            is A.Global -> global.name
            is A.Unop -> {
                //"(" + " ${this.op.symbol} " + this.expr.dump() + ")"
                "$op(${this.expr.dump()})"
            }
            is A.Binop -> {
                val ld = l.dump()
                val rd = r.dump()
                //when (op){
                //    //Wasm.Ops.Op_i32_add -> "($ld + $rd)"
                //    else -> "$op($ld, $rd)"
                //}
                "$op($ld, $rd)"
            }
            is A.CALL -> {
                val name = if (this.func.name.startsWith("___syscall")) "___syscall" else this.func.name
                "$name(${this.args.map { it.dump() }.joinToString(", ")})"
            }
            //is A.CALL_INDIRECT -> "((Op_getFunction(${this.address.dump()}) as (${this.type.type()}))" + "(" + this.args.map { it.dump() }.joinToString(
            is A.CALL_INDIRECT -> "(functions${this.type.signature}(${this.address.dump()})(" + this.args.map { it.dump() }.joinToString(
                ", "
            ) + "))"
            is A.ReadMemory -> {
                //this.access()
                "${this.op}(${this.address.dump()}, ${this.offset}, ${this.align})"
            }
            is A.Phi -> "phi_${this.type}"
            else -> "???($this)"
        }
    }

    fun Wasm.WasmType.default(): String = when (this) {
        Wasm.WasmType.i64 -> "0L"
        Wasm.WasmType.f32 -> "0f"
        Wasm.WasmType.f64 -> "0.0"
        else -> "0"
    }

    fun Wasm.WasmType.type(): String = when (this) {
        Wasm.WasmType.void -> "Unit"
        Wasm.WasmType.i32 -> "Int"
        Wasm.WasmType.i64 -> "Long"
        Wasm.WasmType.f32 -> "Float"
        Wasm.WasmType.f64 -> "Double"
        is Wasm.WasmType.Function -> {
            "(${this.args.joinToString(", ") { it.type() }}) -> ${this.retType.type()}"
        }
        else -> "$this"
    }

    fun AstLabel.goto() = "${this.kind.keyword}@${this.name}"

    fun A.Stm.dump(out: Indenter = Indenter { }): Indenter {
        when (this) {
            is A.Stms -> {
                for (e in stms) e.dump(out)
            }
            is A.AssignLocal -> out.line("${this.local.name} = ${this.expr.dump()}")
            is A.AssignGlobal -> out.line("${this.global.name} = ${this.expr.dump()}")
            is A.RETURN -> out.line("return ${this.expr.dump()}")
            is A.RETURN_VOID -> out.line("return")
            is A.BLOCK -> {
                out.line("${label.name}@do") {
                    this.stm.dump(out)
                }
                out.line("while (false)")
            }
            is A.LOOP -> {
                out.line("${label.name}@while (true)") {
                    this.stm.dump(out)
                    out.line("break")
                }
            }
            is A.IF -> {
                out.line("if (${this.cond.dump()} != 0)") {
                    this.btrue.dump(out)
                }
            }
            is A.IF_ELSE -> {
                out.line("if (${this.cond.dump()} != 0)") {
                    this.btrue.dump(out)
                }
                out.line("else") {
                    this.bfalse.dump(out)
                }
            }
            is A.BR -> {
                out.line(this.label.goto())
            }
            is A.BR_IF -> {
                out.line("if (${this.cond.dump()} != 0) ${this.label.goto()}")
            }
            is A.BR_TABLE -> {
                out.line("when (${this.subject.dump()})") {
                    for ((index, label) in this.labels.withIndex()) {
                        out.line("$index -> ${label.goto()}")
                    }
                    out.line("else -> ${this.default.goto()}")
                }
            }
            is A.STM_EXPR -> {
                out.line(this.expr.dump())
            }
            is A.WriteMemory -> {
                //out.line("${this.access()} = ${this.value.dump()}")
                out.line("${this.op}(${this.address.dump()}, ${this.offset}, ${this.align}, ${this.value.dump()})")
            }
            is A.SetPhi -> {
                out.line("phi_${this.blockType} = ${this.value.dump()}")
            }
            is A.Unreachable -> {
                out.line("// Unreachable")
            }
            is A.NOP -> {
                out.line("// nop")
            }
            else -> out.line("??? $this")
        }
        return out
    }
}
