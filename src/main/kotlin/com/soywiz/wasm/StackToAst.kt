package com.soywiz.wasm

import com.soywiz.kds.*
import com.soywiz.korio.error.*

enum class FlowKind(val keyword: String) {
    BREAK("break"), CONTINUE("continue")
}

class AstLabel(val kind: FlowKind, val name: String, val blockType: WasmType) {
    constructor(kind: FlowKind, index: Int, blockType: WasmType) : this(kind, "label$index", blockType)
}

fun Wasm.Expr.toAst(wasm: WasmModule, func: WasmFunc): Wast.Stm {
    val ast2 = toAst2(AstCtx(wasm, func), AstStack(), func.type.retType, func.type, 0)
    //println(ast2)
    return ast2
}

class AstCtx(val wasm: WasmModule, val func: WasmFunc) {
    private val labels = arrayListOf<AstLabel>()
    private var lastLabel = 1

    fun getLabel(index: Int): AstLabel {
        return labels.getOrNull(labels.size - 1 - index) ?: invalidOp("Can't find label $index")
    }

    fun allocLabel(kind: FlowKind, blockType: WasmType, callback: (label: AstLabel) -> Unit) {
        val label = AstLabel(kind, lastLabel++, blockType)
        labels += label
        try {
            callback(label)
        } finally {
            labels.removeAt(labels.size - 1)
        }
    }
}

class AstStack {
    val stack = ArrayList<Wast.Expr>()

    fun push(e: Wast.Expr) {
        stack.add(e)
    }

    fun pop(): Wast.Expr? {
        if (stack.size == 0) return null
        return stack.removeAt(stack.size - 1)
    }

    fun isNotEmpty() = stack.isNotEmpty()

    fun clone(kind: String): AstStack {
        // @TODO:
        if (stack.isNotEmpty()) {
            //println("WARNING: Not empty stack entering block! kind=$kind, stack=${stack.toList()}")
            return AstStack()
        } else {
            return AstStack()
        }
    }
}

fun Wasm.Expr.toAst2(
    ctx: AstCtx,
    stack: AstStack,
    blockType: WasmType,
    funcType: WasmType.Function,
    level: Int
): Wast.Stm {
    val stms = kotlin.collections.arrayListOf<Wast.Stm>()
    fun addStm(stm: Wast.Stm) {
        //println("STM: $stm")
        stms += stm
    }

    fun stackPush(e: Wast.Expr) = stack.push(e)
    fun stackPop(): Wast.Expr =
        stack.pop() ?: Wast.InvalidExpr(WasmType.i32, "empty stack blockType=$blockType, level=$level")

    fun stackPushPhi(type: WasmType) {
        if (type != WasmType.void) {
            stackPush(Wast.Phi(type))
        }
    }

    fun endOfBlock(blockType: WasmType) {

        if (stack.isNotEmpty()) {
            val items = stack.stack.toList()

            val isVoid = blockType == WasmType.void
            val requiredItems = if (isVoid) 0 else 1

            for (n in 0 until items.size - requiredItems) {
                addStm(Wast.STM_EXPR(stackPop()))
            }

            //if (items.size != 1) {
            //    println("ERROR: Just supported tail stacks of size=1 blockType=$blockType but size=${items.size} :: $items")
            //}

            if (blockType != WasmType.void) {
                if (level == 0) {
                    addStm(Wast.RETURN(stackPop()))
                } else {
                    addStm(Wast.SetPhi(blockType, stackPop()))
                }
            } else {
                //println("ERROR: Block expected to be void but found $items")
                if (stack.isNotEmpty()) addStm(Wast.RETURN(stackPop()))
            }
            //println("STACK IS NOT EMPTY!: (${items.size}) $items")
            //println("######")
        }
    }

    fun flushStack() {
        //val size1 = stack.stack
        val ss = stack.stack.toList()
        stack.stack.clear()
        for ((i, ii) in ss.withIndex()) {
            val type = ii.type
            if (ii is Wast.Const) {
                stack.push(ii)
            } else {
                val lindex = MIN_TEMP_VARIABLE + i + (type.id * 10000)
                if (ii is Wast.Local && ii.local.index == lindex) {
                    stack.push(ii)
                } else {
                    addStm(Wast.SetLocal(AstLocal(lindex, type), ii))
                    stack.push(Wast.Local(AstLocal(lindex, type)))
                }
            }
        }
        //val size2 = stack.stack
        //if (size1 != size2) {
        //    println("ERROR!")
        //}
    }

    fun getLocal(index: Int): AstLocal {
        val locals = ctx.func.rlocals
        val type = locals.getOrNull(index)?.type ?: WasmType.i32
        return AstLocal(index, type) // @TODO: Type
    }

    fun getGlobal(index: Int): AstGlobal {
        return AstGlobal(ctx.wasm, index, ctx.wasm.globals[index]?.globalType?.type ?: WasmType.i32) // @TODO: Type
    }

    loop@ for (i in this.ins) {
        //println(i)
        when (i) {
            is WasmInstruction.InsInt -> {
                when (i.op) {
                    WasmOp.Op_get_local -> stackPush(Wast.Local(getLocal(i.param)))
                    WasmOp.Op_get_global -> stackPush(Wast.Global(getGlobal(i.param)))
                    WasmOp.Op_set_local, WasmOp.Op_tee_local -> {
                        val assignment = Wast.SetLocal(getLocal(i.param), stackPop())
                        flushStack()
                        addStm(assignment)
                        if (i.op == WasmOp.Op_tee_local) {
                            stackPush(Wast.Local(getLocal(i.param)))
                        }
                    }
                    WasmOp.Op_set_global -> {
                        val assignment = Wast.SetGlobal(getGlobal(i.param), stackPop())
                        flushStack()
                        addStm(assignment)
                    }
                    else -> kotlin.TODO("Unimplemented(InsInt) $i :: ${i.javaClass}")
                }
            }
            is WasmInstruction.InsConst -> {
                when (i.op) {
                    WasmOp.Op_i32_const -> stackPush(Wast.Const(WasmType.i32, i.param))
                    WasmOp.Op_i64_const -> stackPush(Wast.Const(WasmType.i64, i.param))
                    WasmOp.Op_f32_const -> stackPush(Wast.Const(WasmType.f32, i.param))
                    WasmOp.Op_f64_const -> stackPush(Wast.Const(WasmType.f64, i.param))
                    else -> kotlin.TODO("Unimplemented(InsLong) $i :: ${i.javaClass}")
                }
            }
            is WasmInstruction.Ins -> {
                when {
                    i.op.istack == 2 && i.op.rstack == 1 -> {
                        val r = stackPop()
                        val l = stackPop()
                        stackPush(Wast.Binop(i.op, l, r))
                    }
                    i.op.istack == 1 && i.op.rstack == 1 -> stackPush(Wast.Unop(i.op, stackPop()))
                    i.op.istack == 1 && i.op.rstack == 0 -> {
                        //stackPop()
                        addStm(Wast.STM_EXPR(stackPop()))
                    }
                    else -> kotlin.TODO("Unimplemented(Ins) $i :: ${i.javaClass}")
                }
            }
            is WasmInstruction.block -> {
                ctx.allocLabel(FlowKind.BREAK, i.b) { label ->
                    addStm(
                        Wast.BLOCK(
                            label,
                            i.expr.toAst2(ctx, stack.clone("block"), i.b, funcType, level + 1)
                        )
                    )
                    stackPushPhi(i.b)
                }
            }
            is WasmInstruction.loop -> {
                ctx.allocLabel(FlowKind.CONTINUE, i.b) { label ->
                    //ctx.allocLabel(FlowKind.BREAK) { label ->
                    addStm(Wast.LOOP(label, i.expr.toAst2(ctx, stack.clone("loop"), i.b, funcType, level + 1)))
                    stackPushPhi(i.b)
                }
            }
            is WasmInstruction.IF -> {
                ctx.allocLabel(FlowKind.BREAK, i.bt) { label ->
                    val cond = stackPop()
                    val btrue = i.btrue.toAst2(ctx, stack.clone("if_true"), i.bt, funcType, level + 1)
                    val bfalse = i.bfalse?.toAst2(ctx, stack.clone("if_false"), i.bt, funcType, level + 1)
                    if (bfalse != null) {
                        addStm(Wast.IF_ELSE(cond, btrue, bfalse))
                    } else {
                        addStm(Wast.IF(cond, btrue))
                    }
                    stackPushPhi(i.bt)
                }
            }
            is WasmInstruction.CALL -> {
                val func = ctx.wasm.functions[i.funcIdx]
                        ?: com.soywiz.korio.error.invalidOp("Can't find function ${i.funcIdx}")
                //println("CALL $func")
                val nargs = func.type.args.count()
                stackPush(Wast.CALL(func.fwt, (0 until nargs).map { stackPop() }.reversed()))
                if (func.type.retTypeVoid) {
                    addStm(Wast.STM_EXPR(stackPop()))
                }
            }
            is WasmInstruction.CALL_INDIRECT -> {
                val type =
                    (ctx.wasm.types.getOrNull(i.typeIdx) as? WasmType.Function)
                            ?: com.soywiz.korio.error.invalidOp("Can't find type ${i.typeIdx}")
                //println("CALL $func")
                val nargs = type.args.size
                val address = stackPop()
                val args = (0 until nargs).map { stackPop() }.reversed()
                stackPush(Wast.CALL_INDIRECT(type, address, args))
                if (type.retTypeVoid) {
                    addStm(Wast.STM_EXPR(stackPop()))
                }
            }
            is WasmInstruction.RETURN -> {
                //println("$i, $stack")
                //println("return")
                if (funcType.retTypeVoid) {
                    addStm(Wast.RETURN_VOID())
                } else {
                    addStm(Wast.RETURN(stackPop()))
                }
            }
            is WasmInstruction.end -> {
                break@loop
            }
            is WasmInstruction.InsMemarg -> {
                when {
                    i.op.istack == 1 && i.op.rstack == 1 -> stackPush(
                        Wast.ReadMemory(
                            i.op,
                            i.align,
                            i.offset,
                            stackPop()
                        )
                    )
                    i.op.istack == 2 && i.op.rstack == 0 -> {
                        val value = stackPop()
                        val address = stackPop()
                        addStm(Wast.WriteMemory(i.op, i.align, i.offset, address, value))
                    }
                    else -> kotlin.TODO("Unimplemented[3] InsMemarg :: $i :: ${i.javaClass}")
                }
            }
            is WasmInstruction.br -> {
                val label = ctx.getLabel(i.l)
                endOfBlock(label.blockType)
                addStm(Wast.BR(label))
                return Wast.Stms(stms)
            }
            is WasmInstruction.br_if -> {
                addStm(Wast.BR_IF(ctx.getLabel(i.l), stackPop()))
            }
            is WasmInstruction.br_table -> {
                addStm(
                    Wast.BR_TABLE(
                        i.labels.map { ctx.getLabel(it) }.withIndex().toList(),
                        ctx.getLabel(i.default),
                        stackPop()
                    )
                )
            }
            is WasmInstruction.unreachable -> {
                addStm(Wast.Unreachable())
            }
            is WasmInstruction.nop -> {
                addStm(Wast.NOP())
            }
            else -> kotlin.TODO("Unimplemented[4] $i :: ${i.javaClass}")
        }
    }

    endOfBlock(blockType)

    return Wast.Stms(stms)
}

data class AstLocal(val name: String, val type: WasmType, val index: Int = -1) {
    constructor(index: Int, type: WasmType) : this("l$index", type, index)

    override fun toString(): String = "$name: $type"
}

data class AstGlobal(val name: String, val type: WasmType) {
    constructor(wasm: WasmModule, index: Int, type: WasmType) : this(
        wasm.globalsByIndex[index]?.name ?: "g$index",
        type
    )
    //val name by lazy { wasm.globals[index]?.name ?: "g$index" }
}

val MIN_TEMP_VARIABLE = 100_000

fun Wast.getLocals(): List<AstLocal> {
    val locals = linkedSetOf<AstLocal>()
    object : WastVisitor() {
        override fun visit(a: AstLocal) = run { locals += a }
    }.visit(this)
    return locals.toList()
}

fun <T> Stack<T>.clear() {
    while (this.isNotEmpty()) pop()
}

fun <T> Stack<T>.toList(): List<T> {
    val out = arrayListOf<T>()
    while (this.isNotEmpty()) out += pop()
    out.reverse()
    for (i in out) push(i)
    return out
}
