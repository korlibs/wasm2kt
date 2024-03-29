package com.soywiz.wasm

import korlibs.io.lang.UTF8
import korlibs.io.lang.invalidOp
import korlibs.io.stream.*
import java.util.LinkedHashMap

// https://webassembly.github.io/spec/core/_download/WebAssembly.pdf

class WasmReader {
    fun toModule() = WasmModule(
        functions = functions.values.toList(),
        datas = datas,
        types = types,
        globals = globals.values.toList(),
        elements = elements.toList()
    )

    companion object {
        val INT_FUNC_TYPE = WasmType.Function(listOf(), listOf(WasmType.i32))

    }

    fun read(s: SyncStream) = s.readModule()

    fun SyncStream.readLEB128_(signed: Boolean): Long {
        var result = 0L
        var shift = 0
        var byte = 0L
        do {
            byte = readU8().toLong()
            result = result or ((byte and 0x7F) shl shift)
            shift += 7
        } while ((byte and 0x80) != 0L)
        if (signed && (byte and 0x40) != 0L) {
            result = result or (0L.inv() shl shift);
        }
        return result
    }

    fun SyncStream.readLEB128() = readLEB128_(false).toInt()
    fun SyncStream.readLEB128S() = readLEB128_(true).toInt()
    fun SyncStream.readLEB128Long(): Long = readLEB128_(false)
    fun SyncStream.readLEB128SLong(): Long = readLEB128_(true)

    fun SyncStream.readName() = readString(readLEB128(), UTF8)

    var types = listOf<WasmType>(); private set
    var functions = LinkedHashMap<Int, WasmFunc>(); private set
    var globals = LinkedHashMap<Int, WasmGlobal>(); private set
    var elements = listOf<WasmElement>(); private set
    var imports = listOf<WasmImport>(); private set
    var exports = listOf<WasmExport>(); private set
    var datas = listOf<WasmData>(); private set

    fun SyncStream.readModule() {
        if (readString(4) != "\u0000asm") invalidOp("Not a WASM file")
        readS32LE()
        while (!eof) readSection()


        /*
        for ((index, function) in functions) {
            println("- ${function.name} ($index) -------------")

            exporter.dump(this@Wasm, function)

            val code = function.code
            if (code != null) {
                val expr = code.expr
                //for (item in expr.ins) println(item)


                val ast = expr.toAst(function.type)
                for ((rindex, local) in (code.locals.flatMap { it }.withIndex())) {
                    val idx = rindex + function.type.args.size
                    println("var l$idx: $local = 0")
                }
                println(ast.dump())
            }
        }
        */
        //println("available: $available")
    }

    // 5.5.2 Sections
    fun SyncStream.readSection() {
        val type = readLEB128()
        val len = readLEB128()
        val content = readStream(len)
        //println("$type")
        content.apply {
            when (type) {
                0 -> {
                    val name = readStringVL()
                    println("Unsupported custom section $type '$name'")
                    //TODO("Unsupported custom section '$name'")
                }
                1 -> readTypesSection()
                2 -> readImportSection()
                3 -> readFunctionSection()
                //4 -> TODO("Unsupported table section")
                //5 -> TODO("Memory section")
                6 -> readGlobalSection()
                7 -> readExportSection()
                //8 -> TODO("Start section")
                9 -> readElementSection()
                10 -> readCodeSection()
                11 -> readDataSection()
                //12 -> TODO("Data count section")
                else -> println("Unsupported section $type")
            }
        }
    }

    fun SyncStream.readData(index: Int): WasmData {
        val memindex = readLEB128()
        val expr = readExpr()
        val data = readBytesExact(readLEB128())
        return WasmData(
            memindex = memindex, data = data, index = index, e = expr
        )
    }

    fun SyncStream.readDataSection() {
        datas = readVec { readData(it) }
        for ((index, data) in datas.withIndex()) {
            trace("// DATA[$index]: ${data.data.size}: ${data.memindex}, ${data.e}")
        }
    }

    fun SyncStream.readCodeLocals(): List<WasmType> {
        val n = readLEB128()
        val type = readType()
        return (0 until n).map { type }
    }

    fun SyncStream.readCode(): WasmCode {
        val size = readLEB128()
        val ss = readBytesExact(size).openSync()
        val locals = ss.readVec { index -> ss.readCodeLocals().map { AstLocal(index, it) } }
        val expr = ss.readExpr()
        return WasmCode(locals, expr)
    }

    fun SyncStream.readCodeSection() {
        val offset = importFunctionsOffset
        for ((index, code) in readVec { readCode() }.withIndex()) {
            functions[offset + index]?.code = code
        }
        for ((index, func) in functions) {
            trace("// CODE[$index]: ${func.code}")
        }
    }

    fun SyncStream.readTypesSection() {
        types = readVec { readType() }
        for ((index, type) in types.withIndex()) {
            trace("// TYPE[$index]: $type")
        }
    }

    private fun trace(str: String) {
        //println(str)
    }

    fun SyncStream.readType(): WasmType {
        val type = readU8()
        //println("%02X".format(type))
        return when (type) {
            0x40 -> WasmType.void
            0x60 -> WasmType.Function(readVec { AstLocal(it, readType()) }, readVec { readType() })
            0x6F -> TODO("externref")
            0x70 -> TODO("funcref")
            0x7F -> WasmType.i32
            0x7B -> WasmType.v128
            0x7E -> WasmType.i64
            0x7D -> WasmType.f32
            0x7C -> WasmType.f64
            else -> invalidOp("Unknown type $type")
        }
    }

    fun SyncStream.readBlockType(): WasmType = readType()

    fun SyncStream.readImportSection() {
        imports = readVec { readImport() }
        for ((index, import) in imports.withIndex()) {
            trace("// IMPORT[$index]: $import")
        }
    }


    fun SyncStream.readMemtype(): WasmType.Limit {
        val limitType = readU8()
        return when (limitType) {
            0x00 -> WasmType.Limit(readLEB128(), null)
            0x01 -> WasmType.Limit(readLEB128(), readLEB128())
            else -> invalidOp("invalid limitType $limitType")
        }
    }

    fun SyncStream.readTableType(): WasmType.TableType {
        val elemType = readU8()
        if (elemType != 0x70) invalidOp("Invalid elementType $elemType")
        return WasmType.TableType(readMemtype())
    }

    fun SyncStream.readGlobalType(): WasmType.Global {
        val t = readType()
        val mut = readU8()
        return WasmType.Global(t, mut != 0)
    }


    val INDEX_FUNCTIONS = 0
    val INDEX_TABLES = 1
    val INDEX_MEMTYPES = 2
    val INDEX_GLOBALS = 3
    val indicesInTables = arrayOf(0, 0, 0, 0)
    val importsInTables = arrayOf(0, 0, 0, 0)
    val importFunctionsOffset get() = importsInTables[INDEX_FUNCTIONS]
    //var functionIndex = 0

    fun SyncStream.readImport(): WasmImport {
        val moduleName = readName()
        val name = readName()
        val indexSpace = readU8()
        val index = indicesInTables[indexSpace]++
        importsInTables[indexSpace] = indicesInTables[indexSpace]
        val type = when (indexSpace) {
            INDEX_FUNCTIONS -> types[readLEB128()]
            INDEX_TABLES -> readTableType()
            INDEX_MEMTYPES -> readMemtype()
            INDEX_GLOBALS -> readGlobalType()
            else -> invalidOp("Unsupported import=$indexSpace")
        }
        val import = WasmImport(moduleName, name, indexSpace, index, type)
        when (indexSpace) {
            INDEX_FUNCTIONS -> functions[index] =
                    WasmFunc(index, type as WasmType.Function, code = null, import = import)
            INDEX_GLOBALS -> globals[index] = WasmGlobal(type as WasmType.Global, index, e = null, import = import)
        }
        //println("$nm::$name = $type")
        return import
    }


    fun SyncStream.readExportSection() {
        exports = readVec { readExport() }
        for ((index, export) in exports.withIndex()) {
            trace("// EXPORT[$index]: $export")
        }
    }

    fun SyncStream.readExport(): WasmExport {
        val name = readName()
        val tid = readU8()
        val idx = readLEB128()
        val obj: Any? = when (tid) {
            0x00 -> functions[idx]
            0x01 -> idx
            0x02 -> idx
            0x03 -> globals[idx]
            else -> invalidOp("Unsupported export=$tid")
        }
        val export = WasmExport(name, obj)

        if (tid == 0) {
            functions[idx]?.export = export
        }

        //println("export[$name] = $tid[$idx] -- $obj")
        return export
    }

    fun SyncStream.readFunctionSection() {
        val funcs = readVec {
            val index = readLEB128()
            WasmFunc(indicesInTables[INDEX_FUNCTIONS]++, types[index] as WasmType.Function)
        }
        for (func in funcs) functions[func.index] = func
        for ((index, func) in functions) {
            trace("// FUNC[$index]: $func")
        }
    }

    fun SyncStream.readGlobalSection() {
        val glbs = readVec { readGlobal() }
        for (g in glbs) globals[g.index] = g
        for ((index, global) in globals) {
            trace("// GLOBAL[$index]: $global")
        }
    }

    fun SyncStream.readTableIdx() = readLEB128()
    fun SyncStream.readFuncIdx() = readLEB128()


    fun SyncStream.readElement(): WasmElement =
        WasmElement(tableIdx = readTableIdx(), expr = readExpr(), funcIdxs = readVec { readFuncIdx() })

    fun SyncStream.readElementSection() {
        elements = readVec { readElement() }

        for ((index, e) in elements.withIndex()) {
            trace("// ELEMENT[$index]: $e")
        }
    }

    fun SyncStream.readGlobal(): WasmGlobal {
        val gt = readGlobalType()
        val e = readExpr()
        trace("// GLOBAL: $gt, $e")
        return WasmGlobal(gt, indicesInTables[INDEX_GLOBALS]++, e)
    }


    //enum class unop {
    //    // int
    //    clz, ctz, popcnt,
    //    // float
    //    abs, neg, sqrt, ceil, floor, trunc, nearest
    //}

    //enum class binop {
    //    add, sub, mul, div_sx, rem_sx, and, or, xor, shl, shr_sx, rotl, rotr,

    //    // float
    //    div, min, max, copysign // add, sub, mul,
    //}


    fun SyncStream.readExpr(): WasmExpr {
        val seq = arrayListOf<WasmInstruction>()
        while (true) {
            val i = readInstr()
            //println("i: $i")
            seq += i
            if (i == WasmInstruction.end || i is WasmInstruction.ELSE) break
        }
        //println("----------")
        return WasmExpr(seq)
    }

    fun SyncStream.peekU8() = keepPosition { readU8() }

    fun <T> SyncStream.readVec(callback: (Int) -> T): List<T> {
        return (0 until readLEB128()).map { callback(it) }
    }

    fun SyncStream.readInstr(): WasmInstruction {
        //println("%08X: ${position.toInt().hex}")
        val op = readU8()
        val oop = WasmOp[op]

        val i = when (op) {
            0x00 -> WasmInstruction.unreachable
            0x01 -> WasmInstruction.nop
            0x02 -> WasmInstruction.block(readBlockType(), readExpr())
            0x03 -> WasmInstruction.loop(readBlockType(), readExpr())
            0x04 -> {
                val bt = readBlockType()
                val in1 = readExpr()
                if (in1.ins.lastOrNull() is WasmInstruction.ELSE) {
                    val _else = in1.ins.last() as WasmInstruction.ELSE
                    WasmInstruction.IF(bt, WasmExpr(in1.ins.dropLast(1)), _else.code)
                } else {
                    WasmInstruction.IF(bt, in1, null)
                }
            }
            0x05 -> WasmInstruction.ELSE(readExpr())
            0x0B -> WasmInstruction.end
            0x0C -> WasmInstruction.br(readLEB128())
            0x0D -> WasmInstruction.br_if(readLEB128())
            0x0E -> WasmInstruction.br_table(readVec { readLEB128() }, readLEB128())
            0x0F -> WasmInstruction.RETURN
            0x10 -> WasmInstruction.CALL(readLEB128())
            0x11 -> WasmInstruction.CALL_INDIRECT(readLEB128(), readU8())
            0x1A, 0x1B -> WasmInstruction.Ins(WasmOp[op])
            in 0x20..0x24 -> WasmInstruction.InsInt(oop, readLEB128())
            in 0x28..0x3E -> WasmInstruction.InsMemarg(oop, readLEB128(), readLEB128())
            0x3F, 0x40 -> WasmInstruction.InsInt(oop, readU8())
            0x41 -> WasmInstruction.InsConst(oop, readLEB128S())
            0x42 -> WasmInstruction.InsConst(oop, readLEB128SLong())
            0x43 -> WasmInstruction.InsConst(oop, readF64LE())
            0x44 -> WasmInstruction.InsConst(oop, readF64LE())
            in 0x45..0xBf -> WasmInstruction.Ins(WasmOp[op])
            else -> invalidOp("Unsupported 0x%02X".format(op))
        }

        //println(" ---> $op: $oop [$i]")
        return i
    }
}

data class WasmExpr(val ins: List<WasmInstruction>)

data class WasmGlobal(
    val globalType: WasmType.Global,
    val index: Int = -1,
    val e: WasmExpr? = null,
    val ast: Wast.Stm? = null,
    var import: WasmImport? = null,
    val name: String = import?.name ?: "g$index"
) {
    val astGlobal = AstGlobal(name, globalType.type)
    //val name get() = import?.name ?: "g$index"
}

data class WasmData(
    val memindex: Int,
    val data: ByteArray,
    val index: Int,
    val e: WasmExpr? = null,
    val ast: Wast.Expr? = null
) {
    fun toAst(module: WasmModule): Wast.Stm = when {
        e != null -> e.toAst(module, WasmFunc(-1, WasmReader.INT_FUNC_TYPE))
        ast != null -> Wast.RETURN(ast)
        else -> TODO()
    }
}

class WasmCode(val locals: List<List<AstLocal>>, val body: WasmExpr) {
    val flatLocals get() = locals.flatMap { it }
}

class WasmCode2(val locals: List<AstLocal>, val body: Wast.Stm) {
}

data class WasmImport(val moduleName: String, val name: String, val indexSpace: Int, val index: Int, val type: Any) {
    val importPair = Pair(moduleName, name)
}
class WasmExport(val name: String, val obj: Any?) {
    override fun toString(): String = "Export($name)"
}

data class WasmElement(
    val tableIdx: Int,
    val funcIdxs: List<Int>? = null,
    val funcNames: List<String>? = null,
    val expr: WasmExpr? = null,
    val exprAst: Wast.Expr? = null
) {
    val funcRefs: List<Any> = (funcIdxs ?: funcNames)!!
}
