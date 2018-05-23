package com.soywiz.wasm

import com.soywiz.kds.*
import com.soywiz.korio.error.*
import com.soywiz.korio.util.*

object WastReader {
    fun parseModule(wast: String) {
        val tokens = StrReader(wast).wastTokenize()
        val block = ListReader(tokens).parseLevel()
        block.parseModule()
        //println(levels)
    }

    fun Block.parseModule() {
        check(this.name == "module")
        val functionTypes = arrayListOf<FunctionType>()
        val globals = arrayListOf<AstGlobalWithInit>()
        for (param in this.params.filterIsInstance<Block>()) {
            when (param.name) {
                "type" -> functionTypes += param.parseType()
                "import" -> param.parseImport()
                "global" -> globals += param.parseGlobal()
                "elem" -> param.parseElem()
                "data" -> param.parseData()
                "export" -> param.parseExport()
                "func" -> param.parseFunc(
                    globals = globals.map { it.global.name to it.global }.toMap(),
                    functionTypes = functionTypes.map { it.name to it.type }.toMap()
                )
                else -> TODO("BLOCK '${param.name}'")
            }
        }
        println(functionTypes)
    }

    fun Block.parseParam(): List<String> {
        check(this.name == "param")
        return params.filterIsInstance<String>()
    }

    data class FunctionType(val name: String, val type: WasmType.Function)

    fun Block.parseType(): FunctionType {
        check(this.name == "type")
        val typeName = string(0)
        fun Block.parseFuncTypeType(): WasmType.Function {
            check(this.name == "func")
            val params = arrayListOf<String>()
            val result = arrayListOf<String>()
            for (param in this.params.filterIsInstance<Block>()) {
                when (param.name) {
                    "param" -> params += param.parseParam()
                    "result" -> result += param.params.filterIsInstance<String>()
                    else -> TODO()
                }
            }
            return WasmType.Function(params.map { WasmType(it) }, result.map { WasmType(it) })
        }

        val type = block(1).parseFuncTypeType()
        return FunctionType(typeName, type)
    }

    fun Block.parseImport() {
        check(this.name == "import")
        val ns = string(0)
        val name = string(1)
        val import = block(2)

        //println("$ns, $name, $import")
        //println(params)
    }

    class AstGlobalWithInit(val global: AstGlobal, val init: Block)

    fun Block.parseGlobal(): AstGlobalWithInit {
        check(this.name == "global")
        val name = string(0)
        val typeBlock = block(1)
        val expr = block(2)
        check(typeBlock.name == "mut")
        val type = WasmType(typeBlock.string(0))
        return AstGlobalWithInit(AstGlobal(name, type), expr)
    }

    fun Block.parseElem() {
        check(this.name == "elem")
    }

    fun Block.parseData() {
        check(this.name == "data")
    }

    fun Block.parseExport() {
        check(this.name == "export")
    }

    fun Block.parseFunc(globals: Map<String, AstGlobal>, functionTypes: Map<String, WasmType.Function>) {
        check(this.name == "func")
        val funcName = string(0)
        val params = arrayListOf<Pair<String, String>>()
        val locals = LinkedHashMap<String, AstLocal>()
        var result = ""
        val body = arrayListOf<Any?>()
        println("funcName: $funcName")
        for (n in 1 until nparams) {
            val block = block(n)
            when (block.name) {
                "param" , "local"-> {
                    val paramName = block.string(0)
                    val paramType = block.string(1)
                    if (block.name == "param") {
                        params += paramName to paramType
                    }
                    locals += paramName to AstLocal(paramName, WasmType(paramType))
                }
                "result" -> {
                    val resultType = block.string(0)
                    result = resultType
                }
                else -> {
                    body += node(block, BlockBuilderContext(labels = mapOf(), locals = locals, globals = globals, functionTypes = functionTypes))
                }
            }
            //println(block)
        }
        println(" --> params=$params, result=$result")
        //println(" --> locals=$locals")
        //println(" --> $body")
    }

    fun expr(block: Block, ctx: BlockBuilderContext): Wast.Expr = node(block, ctx) as Wast.Expr
    fun stm(block: Block, ctx: BlockBuilderContext): Wast.Stm {
        val node = node(block, ctx)
        return when (node) {
            is Wast.Stm -> node
            is Wast.Expr -> Wast.STM_EXPR(node)
            else -> invalidOp("No STM or Expr")
        }
    }

    data class BlockBuilderContext(
        val labels: Map<String, AstLabel>,
        val locals: Map<String, AstLocal>,
        val globals: Map<String, AstGlobal>,
        val functionTypes: Map<String, WasmType.Function>
    )

    fun node(block: Block, ctx: BlockBuilderContext): Wast = block.run {
        //Wasm.Instruction.

        //if (comment != null) println("COMMENT: $comment")

        val op = WasmOp(name)
        return when (op.kind) {
            WasmOp.Kind.LOCAL_GLOBAL -> {
                when (op) {
                    WasmOp.Op_set_local -> Wast.SetLocal(local(0, ctx), expr(1, ctx))
                    WasmOp.Op_tee_local -> Wast.TeeLocal(local(0, ctx), expr(1, ctx))
                    WasmOp.Op_get_local -> Wast.Local(local(0, ctx))
                    WasmOp.Op_set_global -> Wast.SetGlobal(global(0, ctx), expr(1, ctx))
                    WasmOp.Op_get_global -> Wast.Global(global(0, ctx))
                    else -> invalidOp
                }
            }
            WasmOp.Kind.LITERAL -> {
                check(nparams == 1)
                when (op) {
                    WasmOp.Op_i32_const -> Wast.Const(WasmType.i32, string(0).toInt())
                    WasmOp.Op_i64_const -> Wast.Const(WasmType.i64, string(0).toLong())
                    WasmOp.Op_f32_const -> Wast.Const(WasmType.f32, string(0).toFloat())
                    WasmOp.Op_f64_const -> Wast.Const(WasmType.f64, string(0).toDouble())
                    else -> invalidOp
                }
            }
            WasmOp.Kind.BINOP -> {
                check(nparams == 2) { "${op.kind} ${op.name} :: $nparams != 2 :: $params" }
                Wast.Binop(op, expr(0, ctx), expr(1, ctx))
            }
            WasmOp.Kind.UNOP -> {
                check(nparams == 1)
                Wast.Unop(op, expr(0, ctx))
            }
            WasmOp.Kind.MEMORY_STORE, WasmOp.Kind.MEMORY_LOAD -> {
                val rparams = arrayListOf<Block>()
                var alignment = 0
                var offset = 0
                for (param in params) {
                    when (param) {
                        is String -> {
                            when {
                                param.startsWith("align=") -> alignment = param.substr(6).toInt()
                                param.startsWith("offset=") -> offset = param.substr(7).toInt()
                                else -> TODO("$param is not 'align='")
                            }
                        }
                        is Block -> rparams += param
                        else -> TODO()
                    }
                }
                if (op.kind == WasmOp.Kind.MEMORY_LOAD) {
                    Wast.ReadMemory(op, alignment, offset, expr(rparams[0], ctx))
                } else {
                    Wast.WriteMemory(op, alignment, offset, expr(rparams[0], ctx), expr(rparams[1], ctx))
                }
            }
            WasmOp.Kind.CALL -> {
                val name = string(0)
                val args = (1 until nparams).map { node(it, ctx) as Wast.Expr }
                if (WasmOp(block.name) == WasmOp.Op_call) {
                    Wast.CALL(func(name, ctx), args)
                } else {
                    Wast.CALL_INDIRECT(funcType(name, ctx), args.first(), args.drop(1))
                }
            }
            WasmOp.Kind.FLOW -> {
                when (op) {
                    WasmOp.Op_if -> {
                        var result: Block? = null
                        val rparams = arrayListOf<Block>()
                        for (param in params) {
                            if (param is Block && param.name == "result") {
                                result = param
                            } else if (param is Block) {
                                rparams += param
                            } else {
                                TODO("Unknown if with non blocks")
                            }
                        }
                        when {
                            result != null -> {
                                check(rparams.size == 3)
                                Wast.Terop(op, expr(rparams[0], ctx), expr(rparams[1], ctx), expr(rparams[2], ctx))
                            }
                            rparams.size == 2 -> Wast.IF(expr(rparams[0], ctx), stm(rparams[1], ctx))
                            rparams.size == 3 -> Wast.IF_ELSE(expr(rparams[0], ctx), stm(rparams[1], ctx), stm(rparams[2], ctx))
                            else -> TODO("Unknown if with nparams=$nparams :: $block")
                        }
                    }
                    WasmOp.Op_return -> {
                        when (nparams) {
                            0 -> Wast.RETURN_VOID()
                            1 -> Wast.RETURN(expr(0, ctx))
                            else -> TODO("Unknown return with nparams=$nparams :: $block")
                        }
                    }
                    WasmOp.Op_br -> {
                        check(nparams == 1)
                        Wast.BR(label(0, ctx))
                    }
                    WasmOp.Op_br_table -> {
                        val labels = arrayListOf<AstLabel>()
                        lateinit var cond: Block
                        for ((index, param) in params.withIndex()) {
                            if (param is String) {
                                labels += label(param, ctx)
                            } else if (param is Block) {
                                check(index == nparams - 1) // Last item
                                cond = param
                            }
                        }
                        Wast.BR_TABLE(labels.dropLast(1), labels.last(), expr(cond, ctx))
                    }
                    WasmOp.Op_block, WasmOp.Op_loop -> {
                        val blockName = if (params.getOrNull(0) is String) {
                            string(0)
                        } else {
                            null
                        }
                        val startIndex = if (blockName != null) 1 else 0
                        val label = blockName?.let {
                            AstLabel(
                                if (op == WasmOp.Op_loop) FlowKind.CONTINUE else FlowKind.BREAK,
                                it,
                                WasmType.void
                            )
                        }
                        val nctx = if (label != null) {
                            ctx.copy(labels = ctx.labels + mapOf(label.name to label))
                        } else {
                            ctx
                        }
                        val nodes = (startIndex until nparams).map { stm(block(it), nctx) }
                        if (op == WasmOp.Op_loop) {
                            Wast.LOOP(label, Wast.Stms(nodes))
                        } else {
                            Wast.BLOCK(label, Wast.Stms(nodes))
                        }
                    }
                    WasmOp.Op_nop -> Wast.NOP()
                    else -> invalidOp
                }
            }
            else -> TODO("'$name'")
        }
    }

    fun label(name: String, ctx: BlockBuilderContext): AstLabel {
        return ctx.labels[name] ?: error("Can't find label '$name'")
    }
    fun func(name: String, ctx: BlockBuilderContext): WasmFuncRef {
        return WasmFuncName(name) { TODO() }
    }
    fun funcType(name: String, ctx: BlockBuilderContext): WasmType.Function {
        return ctx.functionTypes[name] ?: error("Can't find function type '$name'")
    }
    fun Block.node(index: Int, ctx: BlockBuilderContext): Wast = this@WastReader.node(this.block(index), ctx)
    fun Block.expr(index: Int, ctx: BlockBuilderContext): Wast.Expr = this@WastReader.node(this.block(index), ctx) as Wast.Expr
    fun Block.label(index: Int, ctx: BlockBuilderContext): AstLabel = label(this.string(index), ctx)
    fun Block.local(index: Int, ctx: BlockBuilderContext): AstLocal {
        val name = this.string(index)
        return ctx.locals[name] ?: error("Can't find local '$name'")
    }
    fun Block.global(index: Int, ctx: BlockBuilderContext): AstGlobal {
        val name = this.string(index)
        return ctx.globals[name] ?: error("Can't find global '$name'")
    }

    interface Token {
        val str: String
    }

    object OPEN_BRAC : Token {
        override val str = "("
    }

    object CLOSE_BRAC : Token {
        override val str = ")"
    }

    data class COMMENT(val comment: String) : Token {
        override val str = comment
    }

    data class Op(val op: String) : Token {
        override val str = op
    }

    data class Str(val string: String) : Token {
        override val str = string
    }

    data class Num(val num: String) : Token {
        override val str = num
    }

    data class Id(val id: String) : Token {
        override val str = id
    }

    data class Block(val name: String, val params: List<Any?>, val comment: String? = null) {
        val nparams get() = params.size
        fun string(index: Int) = params[index] as? String? ?: error("$this at index=$index is not a String")
        fun block(index: Int) = params[index] as? Block? ?: error("$this at index=$index is not a Block")
        override fun toString(): String = "($name ${params.joinToString(" ")})"
    }

    fun ListReader<Token>.parseLevel(comment: String? = null): Block {
        val out = arrayListOf<Any?>()
        var rcomment: String? = null
        loop@ while (hasMore) {
            val item = peek()
            when (item) {
                OPEN_BRAC -> {
                    read()
                    val block = parseLevel(rcomment)
                    rcomment = null
                    out.add(block)
                    if (eof) break@loop
                    check(read() == CLOSE_BRAC)
                }
                CLOSE_BRAC -> {
                    rcomment = null
                    break@loop
                }
                is COMMENT -> {
                    rcomment = read().str
                }
                else -> {
                    out.add(read().str)
                    rcomment = null
                }
            }
        }
        if (out.firstOrNull() is Block) return out.first() as Block
        return Block(out.first().toString(), out.drop(1), comment)
    }


    fun StrReader.wastTokenize(): List<Token> {
        val out = arrayListOf<Token>()
        while (!eof) {
            val peek = peek()
            when (peek) {
                ' ', '\t', '\r', '\n' -> {
                    readChar() // skip
                }
                ';' -> {
                    val comment = readUntil('\n') ?: ""
                    out += COMMENT(comment)
                }
                '(' -> run { read(); out += OPEN_BRAC }
                ')' -> run { read(); out += CLOSE_BRAC }
                '=' -> run { out += Op(read(1)) }
                '"' -> {
                    readChar()
                    var str = ""
                    while (!eof) {
                        val pp = peek()
                        if (pp == '\\') {
                            val p1 = read()
                            val p2 = read()
                            when (p2) {
                                '\\' -> str += '\\'
                                '\"' -> str += '\"'
                                '\'' -> str += '\''
                                't' -> str += '\t'
                                'r' -> str += '\r'
                                'n' -> str += '\n'
                                in '0'..'9', in 'a'..'f', in 'A'..'F' -> {
                                    val p3 = read()
                                    str += "$p2$p3".toInt(16).toChar()
                                }
                                else -> TODO("unknown string escape sequence $p1$p2")
                            }
                        } else if (pp == '\"') {
                            break
                        } else {
                            str += read()
                        }
                    }
                    out += Str(str)
                    readChar()
                }
                in '0'..'9', '-' -> {
                    out += Num(readWhile { it in '0'..'9' || it == '.' || it == 'e' || it == 'E' || it == '-' })
                }
                in 'a'..'z', in 'A'..'Z', '$', '%', '_', '.', '/' -> {
                    out += Id(readWhile { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '$' || it == '%' || it == '_' || it == '.' || it == '/' || it == '-' || it == '+' || it == '=' })
                }
                else -> invalidOp("Unknown '$peek'")
            }
        }
        return out
    }

    fun StrReader.parseType() {
        if (tryRead("(type")) {
            skipSpaces()
            val str = readUntil(' ')
            TODO("$str")
        }
    }
}