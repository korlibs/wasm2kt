package com.soywiz.wasm

interface WasmInstruction {
    val op: WasmOp

    object end : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_end
    }

    object unreachable : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_unreachable
    }

    object nop : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_nop
    }

    data class block(val b: WasmType, val expr: WasmExpr) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_block
    }

    data class loop(val b: WasmType, val expr: WasmExpr) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_loop
    }

    data class IF(val bt: WasmType, val btrue: WasmExpr, val bfalse: WasmExpr?) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_if
    }

    data class ELSE(val code: WasmExpr) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_else
    }

    data class br(val l: Int) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_br
    }

    data class br_if(val l: Int) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_br_if
    }

    data class br_table(val labels: List<Int>, val default: Int) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_br_table
    }

    object RETURN : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_return
    }

    data class CALL(val funcIdx: Int) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_call
    }

    data class CALL_INDIRECT(val typeIdx: Int, val zero: Int) : WasmInstruction {
        override val op: WasmOp = WasmOp.Op_call_indirect
    }

    data class Ins(override val op: WasmOp) : WasmInstruction
    data class InsInt(override val op: WasmOp, val param: Int) : WasmInstruction
    data class InsConst(override val op: WasmOp, val param: Any) : WasmInstruction {
        val paramInt get() = param as Int
        val paramLong get() = param as Long
        val paramFloat get() = param as Float
        val paramDouble get() = param as Double
    }

    data class InsMemarg(override val op: WasmOp, val align: Int, val offset: Int) : WasmInstruction
}
