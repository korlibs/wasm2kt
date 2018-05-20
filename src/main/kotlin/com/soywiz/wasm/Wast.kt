package com.soywiz.wasm

import com.soywiz.kds.*
import com.soywiz.korio.error.*
import com.soywiz.korio.util.*

object Wast {
    fun parseModule(wast: String) {
        val tokens = StrReader(wast).wastTokenize()
        val block = ListReader(tokens).parseLevel()
        block.parseModule()
        //println(levels)
    }

    fun Block.parseModule() {
        check(this.name == "module")
        val types = arrayListOf<FunctionType>()
        for (param in this.params.filterIsInstance<Block>()) {
            when (param.name) {
                "type" -> types += param.parseType()
                "import" -> param.parseImport()
                "global" -> param.parseGlobal()
                "elem" -> param.parseElem()
                "data" -> param.parseData()
                "export" -> param.parseExport()
                "func" -> param.parseFunc()
                else -> TODO("BLOCK '${param.name}'")
            }
        }
        println(types)
    }

    fun Block.parseParam(): List<String> {
        check(this.name == "param")
        return params.filterIsInstance<String>()
    }

    data class FunctionTypeType(val params: List<String>, val result: List<String>)
    data class FunctionType(val name: String, val type: FunctionTypeType)

    fun Block.parseType(): FunctionType {
        check(this.name == "type")
        val typeName = string(0)
        fun Block.parseFuncTypeType(): FunctionTypeType {
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
            return FunctionTypeType(params, result)
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

    fun Block.parseGlobal() {
        check(this.name == "global")
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

    fun Block.parseFunc() {
        check(this.name == "func")
        val funcName = string(0)
        val params = arrayListOf<Pair<String, String>>()
        val locals = arrayListOf<Pair<String, String>>()
        var result = ""
        val body = arrayListOf<Any?>()
        println("funcName: $funcName")
        for (n in 1 until nparams) {
            val block = block(n)
            when (block.name) {
                "param" -> {
                    val paramName = block.string(0)
                    val paramType = block.string(1)
                    params += paramName to paramType
                }
                "local" -> {
                    val paramName = block.string(0)
                    val paramType = block.string(1)
                    locals += paramName to paramType
                }
                "result" -> {
                    val resultType = block.string(0)
                    result = resultType
                }
                else -> {
                    body += node(block)
                }
            }
            //println(block)
        }
        println(" --> params=$params, result=$result")
        //println(" --> locals=$locals")
        //println(" --> $body")
    }

    fun node(block: Block): Node = block.run {
        //Wasm.Instruction.

        return when (name) {
            "set_global" -> set_global(string(0), node(1))
            "set_local" -> set_local(string(0), node(1))
            "get_global" -> get_global(string(0))
            "get_local" -> get_local(string(0))

        // Constants
            "i32.const",
            "i64.const",
            "f32.const",
            "f64.const",
            "-----------------------"
            ->
                literal(name, string(0))

            "f32.add", "f32.sub", "f32.mul", "f32.div", "f32.rem", "f32.min", "f32.max", "f32.copysign",
            "f64.add", "f64.sub", "f64.mul", "f64.div", "f64.rem", "f64.min", "f64.max", "f64.copysign",

            "i32.add", "i32.sub", "i32.mul", "i32.div_s", "i32.rem_s", "i32.div_u", "i32.rem_u",
            "i32.and", "i32.or", "i32.xor", "i32.shl", "i32.shr_s", "i32.shr_u", "i32.rotl", "i32.rotr",

            "i64.add", "i64.sub", "i64.mul", "i64.div_s", "i64.rem_s", "i64.div_u", "i64.rem_u",
            "i64.and", "i64.or", "i64.xor", "i64.shl", "i64.shr_s", "i64.shr_u", "i64.rotl", "i64.rotr",

            "i32.eq", "i32.ne",
            "i32.lt_s", "i32.gt_s", "i32.le_s", "i32.ge_s",
            "i32.lt_u", "i32.gt_u", "i32.le_u", "i32.ge_u",

            "i64.eq", "i64.ne",
            "i64.lt_s", "i64.gt_s", "i64.le_s", "i64.ge_s",
            "i64.lt_u", "i64.gt_u", "i64.le_u", "i64.ge_u",

            "f32.eq", "f32.ne",
            "f32.lt", "f32.gt", "f32.le", "f32.ge",

            "f64.eq", "f64.ne",
            "f64.lt", "f64.gt", "f64.le", "f64.ge",

            "----------"
            -> {
                binop(name, node(0), node(1))
            }
            "i32.neg",
            "i64.neg",
            "f32.neg",
            "f64.neg",

            "f32.abs",
            "f64.abs",

            "i64.reinterpret/f64",
            "f64.reinterpret/i64",

            "i32.reinterpret/f32",
            "f32.reinterpret/i32",

            "f32.convert_s/i32",
            "f32.convert_u/i32",
            "f64.convert_s/i32",
            "f64.convert_u/i32",
            "i32.trunc_u/f64",
            "f32.demote/f64",
            "i32.trunc_s/f64",
            "i32.trunc_s/f32",
            "f64.promote/f32",
            "i64.extend_s/i32",
            "i64.extend_u/i32",
            "i32.wrap/i64",
            "i32.eqz",
            "drop" ->
                unop(name, node(0))

            "i32.store",
            "i32.store8",
            "i32.store16",
            "i64.store",
            "f64.store" -> {
                val rparams = arrayListOf<Block>()
                var alignment = 0
                for (param in params) {
                    if (param is String) {
                        if (!param.startsWith("align=")) TODO("$param is not 'align='")
                        alignment = param.substr(6).toInt()
                    } else if (param is Block) {
                        rparams += param
                    } else {
                        TODO()
                    }
                }
                store(name, node(rparams[0]), node(rparams[1]), alignment)
            }
            "i32.load",
            "i32.load8_s",
            "i32.load8_u",
            "i32.load16_s",
            "i32.load16_u",
            "i64.load",
            "f64.load",
            "-------------"
            -> {
                val rparams = arrayListOf<Block>()
                var alignment = 0
                for (param in params) {
                    if (param is String) {
                        if (!param.startsWith("align=")) TODO("$param is not 'align='")
                        alignment = param.substr(6).toInt()
                    } else if (param is Block) {
                        rparams += param
                    } else {
                        TODO()
                    }
                }
                load(name, node(rparams[0]), alignment)
            }
            "if" -> {
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
                    rparams.size == 2 -> nif(result, node(rparams[0]), node(rparams[1]))
                    rparams.size == 3 -> nif(result, node(rparams[0]), node(rparams[1]), node(rparams[2]))
                    else -> TODO("Unknown if with nparams=$nparams :: $block")
                }
            }
            "call", "call_indirect" -> {
                val name = string(0)
                val args = (1 until nparams).map { node(it) }
                if (block.name == "call") {
                    ncall(name, args)
                } else {
                    ncallindirect(name, args)
                }
            }
            "return" -> {
                when (nparams) {
                    0 -> nreturn()
                    1 -> nreturn(node(0))
                    else -> TODO("Unknown return with nparams=$nparams :: $block")
                }
            }
            "br" -> {
                check(nparams == 1)
                nbr(string(0))
            }
            "br_table" -> {
                val labels = arrayListOf<String>()
                lateinit var cond: Block
                for ((index, param) in params.withIndex()) {
                    if (param is String) {
                        labels += param
                    } else if (param is Block) {
                        check(index == nparams - 1) // Last item
                        cond = param
                    }
                }
                nbr_table(node(cond), labels)
            }
            "block", "loop" -> {
                val blockName = if (params.getOrNull(0) is String) {
                    string(0)
                } else {
                    null
                }
                val startIndex = if (blockName != null) 1 else 0
                val nodes = (startIndex until nparams).map { node(it) }
                if (name == "loop") {
                    nloop(blockName, nodes)
                } else {
                    nblock(blockName, nodes)
                }
            }
            "nop" -> nop
            else -> TODO("'$name'")
        }
    }

    fun Block.node(index: Int) = this@Wast.node(this.block(index))

    interface Node
    object nop : Node
    data class nif(val result: Block?, val cond: Node, val btrue: Node, val bfalse: Node? = null) : Node
    data class ncall(val name: String, val args: List<Node>) : Node
    data class ncallindirect(val name: String, val args: List<Node>) : Node
    data class nloop(val name: String?, val stms: List<Node>) : Node
    data class nblock(val name: String?, val stms: List<Node>) : Node
    data class nreturn(val value: Node? = null) : Node
    data class nbr(val label: String) : Node
    data class nbr_table(val cond: Node, val labels: List<String>) : Node
    data class literal(val op: String, val lit: String) : Node
    data class binop(val op: String, val l: Node, val r: Node) : Node
    data class unop(val op: String, val expr: Node) : Node
    data class set_global(val name: String, val expr: Node) : Node
    data class set_local(val name: String, val expr: Node) : Node
    data class get_global(val name: String) : Node
    data class get_local(val name: String) : Node

    data class store(val op: String, val addr: Node, val value: Node, val alignment: Int = 0) : Node
    data class load(val op: String, val addr: Node, val alignment: Int = 0) : Node

    interface Token {
        val str: String
    }

    object OPEN_BRAC : Token {
        override val str = "("
    }

    object CLOSE_BRAC : Token {
        override val str = ")"
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

    data class Block(val name: String, val params: List<Any?>) {
        val nparams get() = params.size
        fun string(index: Int) = params[index] as? String? ?: error("$this at index=$index is not a String")
        fun block(index: Int) = params[index] as? Block? ?: error("$this at index=$index is not a Block")
        override fun toString(): String = "($name ${params.joinToString(" ")})"
    }

    fun ListReader<Token>.parseLevel(): Block {
        val out = arrayListOf<Any?>()
        loop@ while (hasMore) {
            val item = peek()
            when (item) {
                OPEN_BRAC -> {
                    read()
                    val block = parseLevel()
                    out.add(block)
                    if (eof) break@loop
                    check(read() == CLOSE_BRAC)
                }
                CLOSE_BRAC -> {
                    break@loop
                }
                else -> {
                    out.add(read().str)
                }
            }
        }
        if (out.firstOrNull() is Block) return out.first() as Block
        return Block(out.first().toString(), out.drop(1))
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
                    val comment = readUntil('\n')
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
                            str += p1
                            str += p2
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