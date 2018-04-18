import com.soywiz.kds.*
import com.soywiz.korio.error.*

enum class FlowKind(val keyword: String) {
    BREAK("break"), CONTINUE("continue")
}

class AstLabel(val kind: FlowKind, val index: Int, val blockType: Wasm.WasmType) {
    val name: String = "label$index"
}

fun Wasm.Expr.toAst(wasm: Wasm, func: Wasm.WasmFunc): A.Stm {
    val ast2 = toAst2(AstCtx(wasm, func), AstStack(), func.type.retType, func.type, 0)
    //println(ast2)
    return ast2
}

class AstCtx(val wasm: Wasm, val func: Wasm.WasmFunc) {
    private val labels = arrayListOf<AstLabel>()
    private var lastLabel = 1

    fun getLabel(index: Int): AstLabel {
        return labels.getOrNull(labels.size - 1 - index) ?: invalidOp("Can't find label $index")
    }

    fun allocLabel(kind: FlowKind, blockType: Wasm.WasmType, callback: (label: AstLabel) -> Unit) {
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
    val stack = ArrayList<A.Expr>()

    fun push(e: A.Expr) {
        stack.add(e)
    }

    fun pop(): A.Expr? {
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
    blockType: Wasm.WasmType,
    funcType: Wasm.WasmType.Function,
    level: Int
): A.Stm {
    val stms = kotlin.collections.arrayListOf<A.Stm>()
    fun addStm(stm: A.Stm) {
        //println("STM: $stm")
        stms += stm
    }

    fun stackPush(e: A.Expr) = stack.push(e)
    fun stackPop(): A.Expr =
        stack.pop() ?: A.InvalidExpr(Wasm.WasmType.i32, "empty stack blockType=$blockType, level=$level")

    fun stackPushPhi(type: Wasm.WasmType) {
        if (type != Wasm.WasmType.void) {
            stackPush(A.Phi(type))
        }
    }

    fun endOfBlock(blockType: Wasm.WasmType) {

        if (stack.isNotEmpty()) {
            val items = stack.stack.toList()

            val isVoid = blockType == Wasm.WasmType.void
            val requiredItems = if (isVoid) 0 else 1

            for (n in 0 until items.size - requiredItems) {
                addStm(A.STM_EXPR(stackPop()))
            }

            //if (items.size != 1) {
            //    println("ERROR: Just supported tail stacks of size=1 blockType=$blockType but size=${items.size} :: $items")
            //}

            if (blockType != Wasm.WasmType.void) {
                if (level == 0) {
                    addStm(A.RETURN(stackPop()))
                } else {
                    addStm(A.SetPhi(blockType, stackPop()))
                }
            } else {
                //println("ERROR: Block expected to be void but found $items")
                if (stack.isNotEmpty()) addStm(A.RETURN(stackPop()))
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
            if (ii is A.Const) {
                stack.push(ii)
            } else {
                val lindex = MIN_TEMP_VARIABLE + i + (type.id * 10000)
                if (ii is A.Local && ii.local.index == lindex) {
                    stack.push(ii)
                } else {
                    addStm(A.AssignLocal(AstLocal(ctx.wasm, lindex, type), ii))
                    stack.push(A.Local(AstLocal(ctx.wasm, lindex, type)))
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
        return AstLocal(ctx.wasm, index, locals.getOrNull(index) ?: Wasm.WasmType.i32) // @TODO: Type
    }

    fun getGlobal(index: Int): AstGlobal {
        return AstGlobal(ctx.wasm, index, ctx.wasm.globals[index]?.globalType?.type ?: Wasm.WasmType.i32) // @TODO: Type
    }

    loop@ for (i in this.ins) {
        //println(i)
        when (i) {
            is Wasm.Instruction.InsInt -> {
                when (i.op) {
                    Wasm.Ops.Op_get_local -> stackPush(A.Local(getLocal(i.param)))
                    Wasm.Ops.Op_get_global -> stackPush(A.Global(getGlobal(i.param)))
                    Wasm.Ops.Op_set_local, Wasm.Ops.Op_tee_local -> {
                        val assignment = A.AssignLocal(getLocal(i.param), stackPop())
                        flushStack()
                        addStm(assignment)
                        if (i.op == Wasm.Ops.Op_tee_local) {
                            stackPush(A.Local(getLocal(i.param)))
                        }
                    }
                    Wasm.Ops.Op_set_global -> {
                        val assignment = A.AssignGlobal(getGlobal(i.param), stackPop())
                        flushStack()
                        addStm(assignment)
                    }
                    else -> kotlin.TODO("Unimplemented(InsInt) $i :: ${i.javaClass}")
                }
            }
            is Wasm.Instruction.InsConst -> {
                when (i.op) {
                    Wasm.Ops.Op_i32_const -> stackPush(A.Const(Wasm.WasmType.i32, i.param))
                    Wasm.Ops.Op_i64_const -> stackPush(A.Const(Wasm.WasmType.i64, i.param))
                    Wasm.Ops.Op_f32_const -> stackPush(A.Const(Wasm.WasmType.f32, i.param))
                    Wasm.Ops.Op_f64_const -> stackPush(A.Const(Wasm.WasmType.f64, i.param))
                    else -> kotlin.TODO("Unimplemented(InsLong) $i :: ${i.javaClass}")
                }
            }
            is Wasm.Instruction.Ins -> {
                when {
                    i.op.istack == 2 && i.op.rstack == 1 -> {
                        val r = stackPop()
                        val l = stackPop()
                        stackPush(A.Binop(i.op, l, r))
                    }
                    i.op.istack == 1 && i.op.rstack == 1 -> stackPush(A.Unop(i.op, stackPop()))
                    i.op.istack == 1 && i.op.rstack == 0 -> {
                        //stackPop()
                        addStm(A.STM_EXPR(stackPop()))
                    }
                    else -> kotlin.TODO("Unimplemented(Ins) $i :: ${i.javaClass}")
                }
            }
            is Wasm.Instruction.block -> {
                ctx.allocLabel(FlowKind.BREAK, i.b) { label ->
                    addStm(
                        A.BLOCK(
                            label,
                            i.expr.toAst2(ctx, stack.clone("block"), i.b, funcType, level + 1)
                        )
                    )
                    stackPushPhi(i.b)
                }
            }
            is Wasm.Instruction.loop -> {
                ctx.allocLabel(FlowKind.CONTINUE, i.b) { label ->
                    //ctx.allocLabel(FlowKind.BREAK) { label ->
                    addStm(A.LOOP(label, i.expr.toAst2(ctx, stack.clone("loop"), i.b, funcType, level + 1)))
                    stackPushPhi(i.b)
                }
            }
            is Wasm.Instruction.IF -> {
                //println("BLOCK TYPE: ${i.bt}")
                //ctx.allocLabel(FlowKind.CONTINUE) { label ->
                ctx.allocLabel(FlowKind.BREAK, i.bt) { label ->
                    val cond = stackPop()
                    val btrue = i.btrue.toAst2(ctx, stack.clone("if_true"), i.bt, funcType, level + 1)
                    val bfalse = i.bfalse?.toAst2(ctx, stack.clone("if_false"), i.bt, funcType, level + 1)
                    if (bfalse != null) {
                        addStm(A.IF_ELSE(label, cond, btrue, bfalse))
                    } else {
                        addStm(A.IF(label, cond, btrue))
                    }
                    stackPushPhi(i.bt)
                }
            }
            is Wasm.Instruction.CALL -> {
                val func = ctx.wasm.functions[i.funcIdx]
                        ?: com.soywiz.korio.error.invalidOp("Can't find function ${i.funcIdx}")
                //println("CALL $func")
                val nargs = func.type.args.count()
                stackPush(A.CALL(func, (0 until nargs).map { stackPop() }.reversed()))
                if (func.type.retTypeVoid) {
                    addStm(A.STM_EXPR(stackPop()))
                }
            }
            is Wasm.Instruction.CALL_INDIRECT -> {
                val type =
                    (ctx.wasm.types.getOrNull(i.typeIdx) as? Wasm.WasmType.Function)
                            ?: com.soywiz.korio.error.invalidOp("Can't find type ${i.typeIdx}")
                //println("CALL $func")
                val nargs = type.args.size
                val address = stackPop()
                val args = (0 until nargs).map { stackPop() }.reversed()
                stackPush(A.CALL_INDIRECT(type, address, args))
                if (type.retTypeVoid) {
                    addStm(A.STM_EXPR(stackPop()))
                }
            }
            is Wasm.Instruction.RETURN -> {
                //println("$i, $stack")
                //println("return")
                if (funcType.retTypeVoid) {
                    addStm(A.RETURN_VOID())
                } else {
                    addStm(A.RETURN(stackPop()))
                }
            }
            is Wasm.Instruction.end -> {
                break@loop
            }
            is Wasm.Instruction.InsMemarg -> {
                when {
                    i.op.istack == 1 && i.op.rstack == 1 -> stackPush(
                        A.ReadMemory(
                            i.op,
                            i.align,
                            i.offset,
                            stackPop()
                        )
                    )
                    i.op.istack == 2 && i.op.rstack == 0 -> {
                        val value = stackPop()
                        val address = stackPop()
                        addStm(A.WriteMemory(i.op, i.align, i.offset, address, value))
                    }
                    else -> kotlin.TODO("Unimplemented[3] InsMemarg :: $i :: ${i.javaClass}")
                }
            }
            is Wasm.Instruction.br -> {
                val label = ctx.getLabel(i.l)
                endOfBlock(label.blockType)
                addStm(A.BR(label))
                return A.Stms(stms)
            }
            is Wasm.Instruction.br_if -> {
                addStm(A.BR_IF(ctx.getLabel(i.l), stackPop()))
            }
            is Wasm.Instruction.br_table -> {
                addStm(A.BR_TABLE(i.labels.map { ctx.getLabel(it) }, ctx.getLabel(i.default), stackPop()))
            }
            is Wasm.Instruction.unreachable -> {
                addStm(A.Unreachable())
            }
            is Wasm.Instruction.nop -> {
                addStm(A.NOP())
            }
            else -> kotlin.TODO("Unimplemented[4] $i :: ${i.javaClass}")
        }
    }

    endOfBlock(blockType)

    return A.Stms(stms)
}

interface A {
    interface MemoryAccess {
        val op: Wasm.Ops
        val align: Int
        val offset: Int
        val address: Expr

        //fun access(): String {
        //    val shift = when (this.op) {
        //        Wasm.Ops.Op_i32_load8_s, Wasm.Ops.Op_i32_load8_u, Wasm.Ops.Op_i32_store8 -> 0
        //        Wasm.Ops.Op_i32_load16_s, Wasm.Ops.Op_i32_load16_u, Wasm.Ops.Op_i32_store16 -> 1
        //        Wasm.Ops.Op_i32_load, Wasm.Ops.Op_i32_store -> 2
        //        Wasm.Ops.Op_i64_load, Wasm.Ops.Op_i64_store -> 3
        //        else -> invalidOp("$op")
        //    }
        //    if (shift < align) {
        //        invalidOp("Unexpected read/write alignment $shift < $align")
        //    }
        //    val heap = when (this.op) {
        //        Wasm.Ops.Op_i32_load8_s, Wasm.Ops.Op_i32_store8 -> "HEAP8S"
        //        Wasm.Ops.Op_i32_load8_u -> "HEAP8U"
        //        Wasm.Ops.Op_i32_load16_s, Wasm.Ops.Op_i32_store16 -> "HEAP16S"
        //        Wasm.Ops.Op_i32_load16_u -> "HEAP16U"
        //        Wasm.Ops.Op_i32_load, Wasm.Ops.Op_i32_store -> "HEAP32"
        //        Wasm.Ops.Op_i64_load, Wasm.Ops.Op_i64_store -> "HEAP64"
        //        else -> invalidOp("$op")
        //    }
        //    return "$heap[(" + this.address.dump() + "+${this.offset}) >> $shift]"
        //}
    }

    interface Imm : A

    interface Expr : A {
        val type: Wasm.WasmType
    }

    data class Const(override val type: Wasm.WasmType, val value: Any) : Expr, Imm
    data class Local(val local: AstLocal) : Expr, Imm {
        override val type: Wasm.WasmType = local.type
    }

    data class Global(val global: AstGlobal) : Expr {
        override val type: Wasm.WasmType = global.type
    }

    data class InvalidExpr(override val type: Wasm.WasmType, val reason: String) : Expr
    data class Unop(val op: Wasm.Ops, val expr: Expr) : Expr {
        override val type: Wasm.WasmType = op.outType
    }

    data class Binop(val op: Wasm.Ops, val l: Expr, val r: Expr) : Expr {
        override val type: Wasm.WasmType = op.outType
    }

    data class CALL(val func: Wasm.WasmFunc, val args: List<Expr>) : Expr {
        override val type: Wasm.WasmType = func.type.retType
    }

    data class CALL_INDIRECT(override val type: Wasm.WasmType.Function, val address: Expr, val args: List<Expr>) : Expr
    data class ReadMemory(
        override val op: Wasm.Ops,
        override val align: Int,
        override val offset: Int,
        override val address: Expr
    ) : Expr, MemoryAccess {
        override val type: Wasm.WasmType = op.outType
    }

    data class Phi(override val type: Wasm.WasmType) : Expr

    interface Stm : A
    data class Stms(val stms: List<Stm>) : Stm
    data class IF(val label: AstLabel, val cond: Expr, val btrue: Stm) : Stm
    data class IF_ELSE(val label: AstLabel, val cond: Expr, val btrue: Stm, val bfalse: Stm) : Stm
    data class BLOCK(val label: AstLabel, val stm: Stm) : Stm
    data class LOOP(val label: AstLabel, val stm: Stm) : Stm
    data class AssignLocal(val local: AstLocal, val expr: Expr) : Stm
    data class AssignGlobal(val global: AstGlobal, val expr: Expr) : Stm
    data class RETURN(val expr: Expr) : Stm
    data class STM_EXPR(val expr: Expr) : Stm
    data class WriteMemory(
        override val op: Wasm.Ops,
        override val align: Int,
        override val offset: Int,
        override val address: Expr,
        val value: Expr
    ) : Stm,
        MemoryAccess

    data class BR(val label: AstLabel) : Stm
    data class BR_IF(val label: AstLabel, val cond: Expr) : Stm
    data class BR_TABLE(val labels: List<AstLabel>, val default: AstLabel, val subject: Expr) : Stm
    data class InvalidStm(val reason: String) : Stm
    class Unreachable() : Stm
    class NOP() : Stm
    data class SetPhi(val blockType: Wasm.WasmType, val value: A.Expr) : Stm
    class RETURN_VOID() : Stm
}

data class AstLocal(val wasm: Wasm, val index: Int, val type: Wasm.WasmType) {
    val name by lazy { "l$index" }
}

data class AstGlobal(val wasm: Wasm, val index: Int, val type: Wasm.WasmType) {
    val name by lazy { wasm.globals[index]?.name ?: "g$index" }
}

val MIN_TEMP_VARIABLE = 100_000

fun A.getLocals(): List<AstLocal> {
    val locals = linkedSetOf<AstLocal>()
    object : AVisitor() {
        override fun visit(a: AstLocal) = run { locals += a }
    }.visit(this)
    return locals.toList()
}

open class AVisitor {
    open fun visit(a: AstLocal) {
    }

    open fun visit(a: AstGlobal) {
    }

    open fun visit(a: A) {
        when (a) {
            is A.Expr -> visit(a)
            is A.Stm -> visit(a)
            else -> TODO()
        }
    }

    open fun visit(a: A.Expr) {
        when (a) {
            is A.Const -> visit(a)
            is A.Local -> visit(a)
            is A.Global -> visit(a)
            is A.InvalidExpr -> visit(a)
            is A.Unop -> visit(a)
            is A.Binop -> visit(a)
            is A.CALL -> visit(a)
            is A.CALL_INDIRECT -> visit(a)
            is A.ReadMemory -> visit(a)
            is A.Phi -> visit(a)
            else -> TODO()
        }
    }

    open fun visit(a: A.Const) {
    }

    open fun visit(a: A.Local) {
        visit(a.local)
    }

    open fun visit(a: A.Global) {
        visit(a.global)
    }

    open fun visit(a: A.InvalidExpr) {
    }

    open fun visit(a: A.Unop) {
        visit(a.expr)
    }

    open fun visit(a: A.Binop) {
        visit(a.l)
        visit(a.r)
    }

    open fun visit(a: A.CALL) {
        for (arg in a.args) visit(arg)
    }

    open fun visit(a: A.CALL_INDIRECT) {
        for (arg in a.args) visit(arg)
    }

    open fun visit(a: A.ReadMemory) {
        visit(a.address)
    }

    open fun visit(a: A.Phi) {
    }

    open fun visit(a: A.Stm) {
        when (a) {
            is A.Stms -> visit(a)
            is A.IF -> visit(a)
            is A.IF_ELSE -> visit(a)
            is A.BLOCK -> visit(a)
            is A.LOOP -> visit(a)
            is A.AssignLocal -> visit(a)
            is A.AssignGlobal -> visit(a)
            is A.RETURN -> visit(a)
            is A.STM_EXPR -> visit(a)
            is A.WriteMemory -> visit(a)
            is A.BR -> visit(a)
            is A.BR_IF -> visit(a)
            is A.BR_TABLE -> visit(a)
            is A.InvalidStm -> visit(a)
            is A.Unreachable -> visit(a)
            is A.NOP -> visit(a)
            is A.SetPhi -> visit(a)
            is A.RETURN_VOID -> visit(a)
            else -> TODO()
        }
    }

    open fun visit(a: A.Stms) {
        for (stm in a.stms) visit(stm)
    }

    open fun visit(a: A.BLOCK) {
        visit(a.stm)
    }

    open fun visit(a: A.LOOP) {
        visit(a.stm)
    }

    open fun visit(a: A.AssignLocal) {
        visit(a.local)
        visit(a.expr)
    }

    open fun visit(a: A.AssignGlobal) {
        visit(a.global)
        visit(a.expr)
    }

    open fun visit(a: A.RETURN) {
        visit(a.expr)
    }

    open fun visit(a: A.RETURN_VOID) {
    }

    open fun visit(a: A.STM_EXPR) {
        visit(a.expr)
    }

    open fun visit(a: A.WriteMemory) {
        visit(a.address)
        visit(a.value)
    }

    open fun visit(a: A.BR) {
    }

    open fun visit(a: A.BR_IF) {
        visit(a.cond)
    }

    open fun visit(a: A.BR_TABLE) {
        visit(a.subject)
    }

    open fun visit(a: A.InvalidStm) {
    }

    open fun visit(a: A.Unreachable) {
    }

    open fun visit(a: A.NOP) {
    }

    open fun visit(a: A.SetPhi) {
        visit(a.value)
    }

    open fun visit(a: A.IF) {
        visit(a.cond)
        visit(a.btrue)
    }

    open fun visit(a: A.IF_ELSE) {
        visit(a.cond)
        visit(a.btrue)
        visit(a.bfalse)
    }
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
