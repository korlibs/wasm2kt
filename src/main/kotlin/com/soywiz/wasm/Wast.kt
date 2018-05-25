package com.soywiz.wasm

interface Wast {
    interface MemoryAccess {
        val op: WasmOp
        val align: Int
        val offset: Int
        val address: Expr

        //fun access(): String {
        //    val shift = when (this.op) {
        //        Ops.Op_i32_load8_s, Ops.Op_i32_load8_u, Ops.Op_i32_store8 -> 0
        //        Ops.Op_i32_load16_s, Ops.Op_i32_load16_u, Ops.Op_i32_store16 -> 1
        //        Ops.Op_i32_load, Ops.Op_i32_store -> 2
        //        Ops.Op_i64_load, Ops.Op_i64_store -> 3
        //        else -> invalidOp("$op")
        //    }
        //    if (shift < align) {
        //        invalidOp("Unexpected read/write alignment $shift < $align")
        //    }
        //    val heap = when (this.op) {
        //        Ops.Op_i32_load8_s, Ops.Op_i32_store8 -> "HEAP8S"
        //        Ops.Op_i32_load8_u -> "HEAP8U"
        //        Ops.Op_i32_load16_s, Ops.Op_i32_store16 -> "HEAP16S"
        //        Ops.Op_i32_load16_u -> "HEAP16U"
        //        Ops.Op_i32_load, Ops.Op_i32_store -> "HEAP32"
        //        Ops.Op_i64_load, Ops.Op_i64_store -> "HEAP64"
        //        else -> invalidOp("$op")
        //    }
        //    return "$heap[(" + this.address.dump() + "+${this.offset}) >> $shift]"
        //}
    }

    interface Imm : Wast

    interface Expr : Wast {
        val type: WasmType
    }

    data class Const(override val type: WasmType, val value: Any) : Expr, Imm
    data class Local(val local: AstLocal) : Expr, Imm {
        override val type: WasmType = local.type
    }

    data class Global(val global: AstGlobal) : Expr {
        override val type: WasmType = global.type
    }

    data class InvalidExpr(override val type: WasmType, val reason: String) : Expr
    data class Unop(val op: WasmOp, val expr: Expr) : Expr {
        override val type: WasmType = op.outType
    }

    data class Binop(val op: WasmOp, val l: Expr, val r: Expr) : Expr {
        override val type: WasmType = op.outType
    }

    data class Terop(val op: WasmOp, val cond: Expr, val etrue: Expr, val efalse: Expr) : Expr {
        override val type: WasmType = op.outType
    }

    data class CALL(val func: WasmFuncRef, val args: List<Expr>) : Expr {
        override val type: WasmType get() = func.func.type.retType
    }

    data class CALL_INDIRECT(override val type: WasmType.Function, val address: Expr, val args: List<Expr>) : Expr
    data class ReadMemory(
        override val op: WasmOp,
        override val align: Int,
        override val offset: Int,
        override val address: Expr
    ) : Expr, MemoryAccess {
        override val type: WasmType = op.outType
    }

    data class Phi(override val type: WasmType) : Expr

    companion object {
        fun Stms(stms: List<Stm>): Stm {
            return if (stms.size == 1) stms[0] else Stms(true, stms)
        }
    }
    data class TeeLocal(val local: AstLocal, val expr: Expr) : Expr {
        init {
            check(local.type == expr.type)
        }
        override val type: WasmType = expr.type
    }

    interface Stm : Wast
    data class Stms(val dummy: Boolean, val stms: List<Stm>) : Stm
    data class IF(val cond: Expr, val btrue: Stm) : Stm
    data class IF_ELSE(val cond: Expr, val btrue: Stm, val bfalse: Stm) : Stm
    data class BLOCK(val label: AstLabel?, val stm: Stm) : Stm
    data class LOOP(val label: AstLabel?, val stm: Stm) : Stm
    data class SetLocal(val local: AstLocal, val expr: Expr) : Stm
    data class SetGlobal(val global: AstGlobal, val expr: Expr) : Stm
    data class RETURN(val expr: Expr) : Stm
    data class STM_EXPR(val expr: Expr) : Stm
    data class WriteMemory(
        override val op: WasmOp,
        override val align: Int,
        override val offset: Int,
        override val address: Expr,
        val value: Expr
    ) : Stm,
        MemoryAccess

    data class BR(val label: AstLabel) : Stm
    data class BR_IF(val label: AstLabel, val cond: Expr) : Stm
    data class BR_TABLE(val labels: List<IndexedValue<AstLabel>>, val default: AstLabel, val subject: Expr) : Stm
    data class InvalidStm(val reason: String) : Stm
    class Unreachable() : Stm
    class NOP() : Stm
    data class SetPhi(val blockType: WasmType, val value: Wast.Expr) : Stm
    class RETURN_VOID() : Stm
}

fun Wast.Stm.first() = if (this is Wast.Stms) this.stms.first() else this
fun Wast.Stm.last() = if (this is Wast.Stms) this.stms.last() else this
