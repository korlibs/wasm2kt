package com.soywiz.wasm

open class WastVisitor {
    open fun visit(a: AstLocal) {
    }

    open fun visit(a: AstGlobal) {
    }

    open fun visit(a: Wast) {
        when (a) {
            is Wast.Expr -> visit(a)
            is Wast.Stm -> visit(a)
            else -> TODO()
        }
    }

    open fun visit(a: Wast.Expr) {
        when (a) {
            is Wast.Const -> visit(a)
            is Wast.Local -> visit(a)
            is Wast.Global -> visit(a)
            is Wast.InvalidExpr -> visit(a)
            is Wast.Unop -> visit(a)
            is Wast.Binop -> visit(a)
            is Wast.CALL -> visit(a)
            is Wast.CALL_INDIRECT -> visit(a)
            is Wast.ReadMemory -> visit(a)
            is Wast.Phi -> visit(a)
            else -> TODO()
        }
    }

    open fun visit(a: Wast.Const) {
    }

    open fun visit(a: Wast.Local) {
        visit(a.local)
    }

    open fun visit(a: Wast.Global) {
        visit(a.global)
    }

    open fun visit(a: Wast.InvalidExpr) {
    }

    open fun visit(a: Wast.Unop) {
        visit(a.expr)
    }

    open fun visit(a: Wast.Binop) {
        visit(a.l)
        visit(a.r)
    }

    open fun visit(a: Wast.CALL) {
        for (arg in a.args) visit(arg)
    }

    open fun visit(a: Wast.CALL_INDIRECT) {
        for (arg in a.args) visit(arg)
    }

    open fun visit(a: Wast.ReadMemory) {
        visit(a.address)
    }

    open fun visit(a: Wast.Phi) {
    }

    open fun visit(a: Wast.Stm) {
        when (a) {
            is Wast.Stms -> visit(a)
            is Wast.IF -> visit(a)
            is Wast.IF_ELSE -> visit(a)
            is Wast.BLOCK -> visit(a)
            is Wast.LOOP -> visit(a)
            is Wast.SetLocal -> visit(a)
            is Wast.SetGlobal -> visit(a)
            is Wast.RETURN -> visit(a)
            is Wast.STM_EXPR -> visit(a)
            is Wast.WriteMemory -> visit(a)
            is Wast.BR -> visit(a)
            is Wast.BR_IF -> visit(a)
            is Wast.BR_TABLE -> visit(a)
            is Wast.InvalidStm -> visit(a)
            is Wast.Unreachable -> visit(a)
            is Wast.NOP -> visit(a)
            is Wast.SetPhi -> visit(a)
            is Wast.RETURN_VOID -> visit(a)
            else -> TODO()
        }
    }

    open fun visit(a: Wast.Stms) {
        for (stm in a.stms) visit(stm)
    }

    open fun visit(a: Wast.BLOCK) {
        visit(a.stm)
    }

    open fun visit(a: Wast.LOOP) {
        visit(a.stm)
    }

    open fun visit(a: Wast.SetLocal) {
        visit(a.local)
        visit(a.expr)
    }

    open fun visit(a: Wast.SetGlobal) {
        visit(a.global)
        visit(a.expr)
    }

    open fun visit(a: Wast.RETURN) {
        visit(a.expr)
    }

    open fun visit(a: Wast.RETURN_VOID) {
    }

    open fun visit(a: Wast.STM_EXPR) {
        visit(a.expr)
    }

    open fun visit(a: Wast.WriteMemory) {
        visit(a.address)
        visit(a.value)
    }

    open fun visit(a: Wast.BR) {
    }

    open fun visit(a: Wast.BR_IF) {
        visit(a.cond)
    }

    open fun visit(a: Wast.BR_TABLE) {
        visit(a.subject)
    }

    open fun visit(a: Wast.InvalidStm) {
    }

    open fun visit(a: Wast.Unreachable) {
    }

    open fun visit(a: Wast.NOP) {
    }

    open fun visit(a: Wast.SetPhi) {
        visit(a.value)
    }

    open fun visit(a: Wast.IF) {
        visit(a.cond)
        visit(a.btrue)
    }

    open fun visit(a: Wast.IF_ELSE) {
        visit(a.cond)
        visit(a.btrue)
        visit(a.bfalse)
    }
}
