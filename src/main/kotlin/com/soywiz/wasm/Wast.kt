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
        for (param in this.params.filterIsInstance<Block>()) {
            when (param.name) {
                "type" -> param.parseType()
                "import" -> param.parseImport()
                "global" -> param.parseGlobal()
                "elem" -> param.parseElem()
                "data" -> param.parseData()
                "export" -> param.parseExport()
                "func" -> param.parseFunc()
                else -> TODO("BLOCK '${param.name}'")
            }
        }
    }

    fun Block.parseType() {
        check(this.name == "type")
        val paramName = params[0].toString()
        val type = params[1]
        println("$paramName, $type")
    }

    fun Block.parseImport() {
        check(this.name == "import")
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
    }

    interface Token { val str: String }
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
        override fun toString(): String = "($name ${params.joinToString(" ")})"
    }

    fun ListReader<Token>.parseLevel(): Block {
        val out = arrayListOf<Any?>()
        loop@while (hasMore) {
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
                    out += Id(readWhile { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '$' || it == '%' || it == '_' || it == '.' || it == '/' })
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