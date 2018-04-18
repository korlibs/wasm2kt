package com.soywiz.wasm

import com.soywiz.korio.error.*

/*
class JavaExporter(val wasm: Wasm) {
    val module = wasm
    fun dump(): Indenter = Indenter {
        line("@kotlin.Suppress(names = {\"UNCHECKED_CAST\", \"UNREACHABLE_CODE\", \"RedundantExplicitType\", \"UNUSED_VARIABLE\", \"VARIABLE_WITH_REDUNDANT_INITIALIZER\"})")
        line("class Module extends WasmModule") {
            line("static public void main(String[] args)") {
                line("new Module()._main(0, 0);")
            }
            for (data in wasm.datas) {
                line("private int computeDataIndex${data.index}()") {
                    line(data.e.toAst(wasm, INT_FUNC_TYPE).dump())
                }
            }
            line("private void init()") {
                for (data in wasm.datas) {
                    line("__putBytes(computeDataIndex${data.index}(), new byte[] {${data.data.joinToString(", ") { "$it" }}});")
                }
            }
            line("public Module()") {
                line("init();")
            }
            for (global in module.globals.values) {
                if (global.import == null) {
                    line("${global.gt.t.type()} compute${global.name}()") {
                        line(global.e!!.toAst(wasm, Wasm.WasmType.Function(listOf(), listOf(global.gt.t))).dump())
                    }
                    line("public ${global.gt.t.type()} ${global.name} = compute${global.name}();")
                }
            }
            for (func in module.functions.values) {
                line(dump(func))
            }
        }
    }

    fun dump(func: Wasm.WasmFunc): Indenter = Indenter {
        val code = func.code
        if (code != null) {
            val body = code.body
            val visibility = if (func.export != null) "public " else "private "
            val args = func.type.args.withIndex().joinToString(", ") { "${it.value.type()} p" + it.index + "" }
            line("${visibility}${func.type.retType.type()} ${func.name}($args)") {
                for ((index, local) in func.type.args.withIndex()) {
                    line("${local.type()} l$index = p$index;")
                }
                for ((index, local) in code.flatLocals.withIndex()) {
                    val rindex = index + func.type.args.size
                    line("${local.type()} l$rindex = 0;")
                }
                line("int phi_i32 = 0;")
                line("long phi_i64 = 0L;")
                line(body.toAst(wasm, func.type).dump())
            }
        }
    }

    fun A.Expr.dump(): String {
        return when (this) {
            is A.Const -> "(${this.value})"
            is A.Local -> "l${this.index}"
            is A.Global -> wasm.globals[index]?.name ?: invalidOp("Unknown global $index")
            is A.Unop -> {
                //"(" + " ${this.op.symbol} " + this.expr.dump() + ")"
                "$op(${this.expr.dump()})"
            }
            is A.Binop -> {
                "$op(${this.l.dump()}, ${this.r.dump()})"
                //"(" + this.l.dump() + " ${this.op.symbol} " + this.r.dump() + ")"
            }
            is A.CALL -> this.func.name + "(" + this.args.map { it.dump() }.joinToString(", ") + ")"
            is A.CALL_INDIRECT -> "(((${this.type.type()})(Op_getFunction(${this.address.dump()}))).invoke(" + this.args.map { it.dump() }.joinToString(
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

    fun Wasm.WasmType.type(): String = when (this) {
        Wasm.WasmType.void -> "void"
        Wasm.WasmType.i32 -> "int"
        Wasm.WasmType.i64 -> "long"
        Wasm.WasmType.f32 -> "float"
        Wasm.WasmType.f64 -> "double"
        is Wasm.WasmType.Function -> {
            val fi = this.argsPlusRet.size - 1
            "kotlin.jvm.functions.Function$fi<${this.argsPlusRet.joinToString(", ") { it.typeGeneric() }}>"
        }
        else -> "$this"
    }

    fun Wasm.WasmType.typeGeneric(): String = when (this) {
        Wasm.WasmType.void -> "Void"
        Wasm.WasmType.i32 -> "Integer"
        Wasm.WasmType.i64 -> "Long"
        Wasm.WasmType.f32 -> "Float"
        Wasm.WasmType.f64 -> "Double"
        is Wasm.WasmType.Function -> type()
        else -> "$this"
    }

    fun A.Stm.dump(out: Indenter = Indenter { }): Indenter {
        when (this) {
            is A.Stms -> {
                for (e in stms) e.dump(out)
            }
            is A.AssignLocal -> out.line("l${this.local} = ${this.expr.dump()};")
            is A.AssignGlobal -> out.line("g${this.global} = ${this.expr.dump()};")
            is A.RETURN -> out.line("return ${this.expr.dump()};")
            is A.BLOCK -> {
                out.line("${label.name}: do") {
                    this.stm.dump(out)
                }
                out.line("while (false);")
            }
            is A.LOOP -> {
                out.line("${label.name}: while (true)") {
                    this.stm.dump(out)
                    out.line("break;")
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
                out.line("${this.label.kind.keyword} ${this.label.name};")
            }
            is A.BR_IF -> {
                out.line("if (${this.cond.dump()} != 0) ${this.label.kind.keyword} ${this.label.name};")
            }
            is A.STM_EXPR -> {
                out.line(this.expr.dump() + ";")
            }
            is A.WriteMemory -> {
                //out.line("${this.access()} = ${this.value.dump()}")
                out.line("${this.op}(${this.address.dump()}, ${this.offset}, ${this.align}, ${this.value.dump()});")
            }
            is A.SetPhi -> {
                out.line("phi_${this.blockType} = ${this.value.dump()};")
            }
            is A.Unreachable -> {
                out.line("throw new RuntimeException()")
            }
            is A.NOP -> {
                out.line("// nop")
            }
            else -> out.line("??? $this")
        }
        return out
    }
}
*/
