package com.soywiz.wasm

import com.soywiz.kds.*
import com.soywiz.korio.error.*
import com.soywiz.korio.util.*

/**
 * This WAST file format is useful for two things:
 * - Instead of a stack-based approach, it uses an AST in SSA form
 * - When added debug information, this includes original private function names and local variable names
 * - Much more useful for generating debuggable code than binary wasm that is unable to produce that information
 */
open class WastReader {
    fun parseModule(wast: String): WasmModule {
        val tokens = StrReader(wast).wastTokenize()
        val block = ListReader(tokens).parseLevel()
        return block.parseModule()
        //println(levels)
    }

    val functionTypes = arrayListOf<FunctionType>()
    val functions = arrayListOf<WasmFunc>()
    val globals = arrayListOf<Wasm.WasmGlobal>()
    val astglobalsByName = LinkedHashMap<String, AstGlobal>()
    val functionTypesByName = LinkedHashMap<String, WasmType.Function>()
    val datas = arrayListOf<Wasm.Data>()
    val elements = arrayListOf<Wasm.Element>()
    val exports = arrayListOf<WastExport>()
    val functionHeaders = LinkedHashMap<String, WasmFuncWithType>()

    fun addFunctionType(type: FunctionType) {
        functionTypes += type
        functionTypesByName[type.name] = type.type
    }

    fun addGlobal(global: Wasm.WasmGlobal) {
        globals += global
        astglobalsByName[global.name] = AstGlobal(global.name, global.globalType.type)
    }

    fun Block.parseModule(): WasmModule {
        check(this.name == "module")
        for (param in this.params.filterIsInstance<Block>()) {
            when (param.name) {
                "import" -> {
                    val import = param.parseImport()
                    val obj = import.obj
                    when (obj) {
                        is WasmFunc -> {
                            functionHeaders[obj.fwt.name] = obj.fwt
                        }
                    }
                }
                "func" -> {
                    val funcHeader = param.parseFuncHeader()
                    functionHeaders[funcHeader.name] = funcHeader
                }
            }
        }
        for (param in this.params.filterIsInstance<Block>()) {
            when (param.name) {
                "type" -> addFunctionType(param.parseType())
                "import" -> {
                    val import = param.parseImport()
                    val obj = import.obj
                    when (obj) {
                        is Wasm.WasmGlobal -> {
                            addGlobal(obj)
                        }
                        is WasmFunc -> {
                            functions += obj
                        }
                    }
                }
                "global" -> addGlobal(param.parseGlobal())
                "elem" -> {
                    elements += param.parseElem()
                }
                "data" -> {
                    datas += param.parseData(datas.size)
                }
                "export" -> {
                    exports += param.parseExport()
                }
                "func" -> {
                    functions += param.parseFunc(
                        globals = astglobalsByName,
                        functionTypes = functionTypesByName
                    )
                }
                else -> TODO("BLOCK '${param.name}'")
            }
        }

        val functionsByName = functions.associateBy { it.name }
        for (export in exports) {
            val func = functionsByName[export.name] ?: error("Can't export ${export.name} -> ${export.exportName}")
            func?.export = Wasm.Export(export.exportName, func.type)
        }

        return WasmModule(
            functions = functions,
            datas = datas,
            types = functionTypes.map { it.type },
            globals = globals,
            elements = elements
        )
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
            return WasmType.Function(
                params.withIndex().map { AstLocal(it.index, WasmType(it.value)) },
                result.map { WasmType(it) })
        }

        val type = block(1).parseFuncTypeType()
        return FunctionType(typeName, type)
    }

    class WastImport(val ns: String, val name: String, val obj: Any) {
        val wasmImport get() = Wasm.Import(ns, name, 0, 0, Unit)
    }

    class ImportMemory(val a1: String, val a2: String, val a3: String)
    class ImportTable(val a1: String, val a2: String, val a3: String)

    fun Block.parseImport(): WastImport {
        check(this.name == "import")
        val ns = string(0)
        val name = string(1)
        val import = block(2)
        val wimport = Wasm.Import(ns, name, -1, -1, Unit)
        val obj: Any = when (import.name) {
            "global" -> {
                val importName = import.string(0)
                val importType = WasmType(import.string(1))
                //println("GLOBAL: $importName: $importType <-- $ns::$name")
                //AstGlobal(importName, importType)
                Wasm.WasmGlobal(
                    globalType = WasmType.Global(importType, false),
                    index = -1,
                    e = null,
                    ast = null,
                    import = wimport,
                    name = importName
                )
            }
            "func" -> {
                val params = arrayListOf<WasmType>()
                val result = arrayListOf<WasmType>()
                val importName = import.string(0)
                for (n in 1 until import.nparams) {
                    val info = import.block(n)
                    val types = (0 until info.nparams).map { WasmType(info.string(it)) }
                    when (info.name) {
                        "param" -> params += types
                        "result" -> result += types
                    }
                }
                val funcType = WasmType.Function(params.withIndex().map { AstLocal(it.index, it.value) }, result)
                //println("FUNC: $importName ($params) -> ($result) <-- $ns::$name")
                WasmFunc(
                    index = -1,
                    type = funcType,
                    code = null,
                    import = wimport,
                    export = null,
                    code2 = null,
                    name2 = importName
                )
            }
            "memory" -> {
                val a1 = import.string(0)
                val a2 = import.string(1)
                val a3 = import.string(2)
                //println("MEMORY: $a1, $a2, $a3 <-- $ns::$name")
                ImportMemory(a1, a2, a3)
            }
            "table" -> {
                val a1 = import.string(0)
                val a2 = import.string(1)
                val a3 = import.string(2)
                //println("TABLE: $a1, $a2, $a3 <-- $ns::$name")
                ImportTable(a1, a2, a3)
            }
            else -> {
                TODO("Import ${import.name}")
            }
        }

        //println("$ns, $name, $import")
        //println(params)
        return WastImport(ns, name, obj)
    }

    fun Block.parseGlobal(): Wasm.WasmGlobal {
        check(this.name == "global")
        val ctx = BlockBuilderContext(globals = astglobalsByName)
        val name = string(0)
        val typeBlock = block(1)
        val expr = expr(2, ctx)
        check(typeBlock.name == "mut")
        val mut = true
        val type = WasmType(typeBlock.string(0))
        return Wasm.WasmGlobal(WasmType.Global(type, mut), ast = Wast.RETURN(expr), name = name)
    }

    fun Block.parseElem(): Wasm.Element {
        check(this.name == "elem")
        val expr = expr(0, BlockBuilderContext(globals = astglobalsByName))
        val functionNames = (1 until nparams).map { string(it) }
        return Wasm.Element(tableIdx = 0, exprAst = expr, funcNames = functionNames)
    }

    fun Block.parseData(index: Int): Wasm.Data {
        check(this.name == "data")
        val expr = expr(0, BlockBuilderContext())
        val data = string(1)
        val dataBA = data.map { it.toByte() }.toByteArray()
        //File("/tmp/mem-1024.bin").writeBytes(dataBA)
        return Wasm.Data(index = index, data = dataBA, memindex = 0, ast = expr)
    }

    class WastExport(val exportName: String, val name: String)

    fun Block.parseExport(): WastExport {
        check(this.name == "export")
        val fexportname = string(0)
        val func = block(1)
        check(func.name == "func")
        val fname = func.string(0)
        return WastExport(fexportname, fname)
    }

    fun Block.parseFuncHeader(): WasmFuncWithType {
        check(this.name == "func")
        val p = reader()
        val funcName = p.string()
        val params = arrayListOf<AstLocal>()
        val results = arrayListOf<WasmType>()
        while (p.hasMore) {
            val b = (p.read() as? Block?) ?: continue
            if (b.name == "param") {
                params += AstLocal(b.string(0), WasmType(b.string(1)))
            } else if (b.name == "result") {
                results += WasmType(b.string(0))
            } else {
                break
            }
        }
        return WasmFuncWithType(funcName, WasmType.Function(params, results))
    }

    fun Block.parseFunc(globals: Map<String, AstGlobal>, functionTypes: Map<String, WasmType.Function>): WasmFunc {
        check(this.name == "func")
        val funcName = string(0)
        val params = arrayListOf<Pair<String, AstLocal>>()
        val results = arrayListOf<WasmType>()
        val locals = LinkedHashMap<String, AstLocal>()
        val localsAndParams = LinkedHashMap<String, AstLocal>()
        var result = ""
        val body = arrayListOf<Wast.Stm>()
        //println("funcName: $funcName")
        for (n in 1 until nparams) {
            val block = block(n)
            when (block.name) {
                "param", "local" -> {
                    val paramName = block.string(0)
                    val paramTypeStr = block.string(1)
                    val paramType = WasmType(paramTypeStr)
                    val pair = paramName to AstLocal(paramName, paramType)
                    if (block.name == "param") {
                        params += pair
                    } else {
                        locals += pair
                    }
                    localsAndParams += pair
                }
                "result" -> {
                    val resultTypeStr = block.string(0)
                    val resultType = WasmType(resultTypeStr)
                    results += resultType
                }
                else -> {
                    body += stm(
                        block,
                        BlockBuilderContext(
                            labels = mapOf(),
                            locals = localsAndParams,
                            globals = globals,
                            functionTypes = functionTypes
                        )
                    )
                }
            }
            //println(block)
        }
        //println(" --> params=$params, result=$result")
        //println(" --> locals=$locals")
        //println(" --> $body")
        return WasmFunc(
            index = -1,
            name2 = funcName,
            type = WasmType.Function(params.map { it.second }, results),
            code2 = Wasm.Code2(locals.map { it.value }, Wast.Stms(body))
        )
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
        val labels: Map<String, AstLabel> = mapOf(),
        val locals: Map<String, AstLocal> = mapOf(),
        val globals: Map<String, AstGlobal> = mapOf(),
        val functionTypes: Map<String, WasmType.Function> = mapOf()
    )

    fun node(block: Block, ctx: BlockBuilderContext): Wast = block.run {
        //Wasm.Instruction.

        //if (comment != null) println("COMMENT: $comment")

        val op = WasmOp(name)
        return when (op.kind) {
            WasmOp.Kind.LOCAL_GLOBAL -> {
                when (op) {
                    WasmOp.Op_set_local -> {
                        check(nparams == 2)
                        Wast.SetLocal(local(0, ctx), expr(1, ctx))
                    }
                    WasmOp.Op_tee_local -> {
                        check(nparams == 2)
                        Wast.TeeLocal(local(0, ctx), expr(1, ctx))
                    }
                    WasmOp.Op_get_local -> {
                        check(nparams == 1)
                        Wast.Local(local(0, ctx))
                    }
                    WasmOp.Op_set_global -> {
                        check(nparams == 2)
                        Wast.SetGlobal(global(0, ctx), expr(1, ctx))
                    }
                    WasmOp.Op_get_global -> {
                        check(nparams == 1)
                        Wast.Global(global(0, ctx))
                    }
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
            WasmOp.Kind.DROP -> {
                check(nparams == 1)
                stm(0, ctx)
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
                    Wast.CALL(functionHeaders[name] ?: error("Can't find function with name $name"), args)
                } else {
                    Wast.CALL_INDIRECT(funcType(name, ctx), args.last(), args.dropLast(1))
                }
            }
            WasmOp.Kind.FLOW -> {
                when (op) {
                    WasmOp.Op_if -> {
                        var resultType: WasmType = WasmType.void
                        val rparams = arrayListOf<Block>()
                        for (param in params) {
                            if (param is Block && param.name == "result") {
                                resultType = WasmType(param.string(0))
                            } else if (param is Block) {
                                rparams += param
                            } else {
                                TODO("Unknown if with non blocks")
                            }
                        }
                        when {
                            resultType != WasmType.void -> {
                                check(rparams.size == 3)
                                Wast.Terop(op, expr(rparams[0], ctx), expr(rparams[1], ctx), expr(rparams[2], ctx))
                            }
                            rparams.size == 2 -> Wast.IF(expr(rparams[0], ctx), stm(rparams[1], ctx))
                            rparams.size == 3 -> Wast.IF_ELSE(
                                expr(rparams[0], ctx),
                                stm(rparams[1], ctx),
                                stm(rparams[2], ctx)
                            )
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
                        check(nparams <= 2) { "$params" }
                        when (nparams) {
                            1 -> Wast.BR(label(0, ctx))
                            2 -> Wast.BR(label(0, ctx), expr(1, ctx))
                            else -> invalidOp
                        }
                    }
                    WasmOp.Op_br_if -> {
                        check(nparams <= 3) { "$params" }
                        when (nparams) {
                            2 -> Wast.BR_IF(label(0, ctx), expr(1, ctx))
                            3 -> Wast.BR_IF(label(0, ctx), expr(2, ctx), result = expr(1, ctx))
                            else -> invalidOp
                        }
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
                        val defaultLabel = labels.last()
                        val cases = labels.dropLast(1).withIndex().toList().filter { it.value != defaultLabel }
                        Wast.BR_TABLE(cases, defaultLabel, expr(cond, ctx))
                    }
                    WasmOp.Op_block, WasmOp.Op_loop -> {
                        val rparams = reader()

                        val blockName = if (rparams.peek() is String) {
                            rparams.string()
                        } else {
                            null
                        }

                        var blockType: WasmType = WasmType.void

                        if (rparams.peek() is Block) {
                            if ((rparams.peek() as Block).name == "result") {
                                blockType = WasmType(rparams.block().string(0))
                                //TODO()
                            }
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
                        val nodes = rparams.restBlock().map { stm(it, nctx) }
                        if (op == WasmOp.Op_loop) {
                            Wast.LOOP(label, Wast.Stms(nodes))
                        } else {
                            if (blockType != WasmType.void) {
                                Wast.BLOCK_EXPR(label, Wast.Stms(nodes), blockType)
                            } else {
                                Wast.BLOCK(label, Wast.Stms(nodes))
                            }
                        }
                    }
                    WasmOp.Op_nop -> Wast.NOP()
                    else -> invalidOp("OP: $op")
                }
            }
            else -> TODO("'$name'")
        }
    }

    fun label(name: String, ctx: BlockBuilderContext): AstLabel {
        return ctx.labels[name] ?: error("Can't find label '$name'")
    }

    fun func(name: String, ctx: BlockBuilderContext): WasmFuncRef {
        return WasmFuncName(name) { TODO("Error getting function '$name'") }
    }

    fun funcType(name: String, ctx: BlockBuilderContext): WasmType.Function {
        return ctx.functionTypes[name] ?: error("Can't find function type '$name'")
    }

    fun Block.node(index: Int, ctx: BlockBuilderContext): Wast = this@WastReader.node(this.block(index), ctx)
    fun Block.expr(index: Int, ctx: BlockBuilderContext): Wast.Expr =
        this@WastReader.node(this.block(index), ctx) as Wast.Expr

    fun Block.stm(index: Int, ctx: BlockBuilderContext): Wast.Stm =
        stm(block(index), ctx)

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

    class ParamsReader(val params: List<Any?>) {
        val reader = ListReader(params)
        val hasMore: Boolean get() = reader.hasMore

        fun rest(): List<Any?> = mapWhile({ reader.hasMore }) { reader.read() }
        fun restBlock(): List<Block> = rest().map { it as Block }
        fun peek() = reader.peek()
        fun read() = reader.read()
        fun string() = reader.read() as? String? ?: error("$this at index=${reader.position} is not a String")
        fun block() = reader.read() as? Block? ?: error("$this at index=${reader.position} is not a Block")
    }

    data class Block(val name: String, val params: List<Any?>, val comment: String? = null) {
        fun reader() = ParamsReader(params)
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
                    var str = StringBuilder()
                    loop@ while (!eof) {
                        val pp = peek()
                        when (pp) {
                            '\\' -> {
                                val p1 = read()
                                val p2 = read()
                                str.append(when (p2) {
                                    '\\' -> '\\'
                                    '\"' -> '\"'
                                    '\'' -> '\''
                                    't' -> '\t'
                                    'r' -> '\r'
                                    'n' -> '\n'
                                    in '0'..'9', in 'a'..'f', in 'A'..'F' -> {
                                        val p3 = read()
                                        val vh = HexUtil.unhex(p2)
                                        val vl = HexUtil.unhex(p3)
                                        ((vh shl 8) or vl).toChar()
                                    }
                                    else -> TODO("unknown string escape sequence $p1$p2")
                                })
                            }
                            '\"' -> {
                                readChar()
                                break@loop
                            }
                            else -> str.append(read())
                        }
                    }
                    out += Str(str.toString())
                }
                in '0'..'9', '-', '+' -> {
                    out += Num(readWhile { it in '0'..'9' || it == '.' || it == 'e' || it == 'E' || it == '-' || it == '+' })
                }
                in 'a'..'z', in 'A'..'Z', '$', '%', '_', '.', '/' -> {
                    out += Id(readWhile { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '$' || it == '%' || it == '_' || it == '.' || it == '/' || it == '-' || it == '+' || it == '=' })
                }
                else -> invalidOp("Unknown '$peek' at ${this.pos}")
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

object HexUtil {
    fun unhex(char: Char): Int = when (char) {
        in '0'..'9' -> char.toInt()
        in 'a'..'f' -> char.toInt() + 10
        in 'A'..'F' -> char.toInt() + 10
        else -> throw RuntimeException("Not an hex character")
    }
}

