package com.soywiz.wasm

import com.soywiz.korio.*
import com.soywiz.korio.error.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.vfs.*
import java.io.*
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class Wasm {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = Korio {
            val margs = LinkedList<String>(args.toList())
            var showHelp = false
            var lang: String = "kotlin"
            var file: String? = null
            if (margs.isEmpty()) showHelp = true
            while (margs.isNotEmpty()) {
                val arg = margs.removeFirst()
                if (arg.startsWith("-")) {
                    when (arg) {
                        "-out" -> {
                            lang = margs.removeFirst()
                        }
                        "-h", "-help", "--help", "-?" -> {
                            showHelp = true
                        }
                    }
                } else {
                    file = arg
                }
            }
            if (showHelp || file == null) error("wasm2kt [-out java] <file.wasm>")
            val data = file.uniVfs.readAll().openSync()
            println(Wasm.readAndConvert(data, lang))
        }

        fun readAndConvert(s: SyncStream, lang: String): Indenter {
            val wasm = Wasm()
            wasm.read(s)
            val exporter = when (lang) {
                "kotlin" -> KotlinExporter(wasm)
                "java" -> JavaExporter(wasm)
                else -> error("Unsupported exporter '$lang'. Only supported 'java' and 'kotlin'.")
            }
            //val exporter = JavaExporter(this@Wasm)
            return exporter.dump()
        }
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
    var globals = LinkedHashMap<Int, Global>(); private set
    var elements = listOf<Element>(); private set
    var imports = listOf<Import>(); private set
    var exports = listOf<Export>(); private set
    var datas = listOf<Data>(); private set

    fun SyncStream.readModule() {
        if (readString(4) != "\u0000asm") invalidOp("Not a WASM file")
        readS32_le()
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

    fun SyncStream.readSection() {
        val type = readLEB128()
        val len = readLEB128()
        val content = readStream(len)
        //println("$type")
        content.apply {
            when (type) {
                1 -> readTypesSection()
                2 -> readImportSection()
                3 -> readFunctionSection()
                6 -> readGlobalSection()
                7 -> readExportSection()
                9 -> readElementSection()
                10 -> readCodeSection()
                11 -> readDataSection()
                else -> println("Unsupported section $type")
            }
        }
    }

    data class Data(val x: Int, val e: Expr, val data: ByteArray, val index: Int)

    fun SyncStream.readData(index: Int): Data {
        val memindex = readLEB128()
        val expr = readExpr()
        val data = readBytesExact(readLEB128())
        return Data(memindex, expr, data, index)
    }

    fun SyncStream.readDataSection() {
        datas = readVec { readData(it) }
        for ((index, data) in datas.withIndex()) {
            trace("// DATA[$index]: ${data.data.size}: ${data.x}, ${data.e}")
        }
    }

    fun SyncStream.readCodeLocals(): List<WasmType> {
        val n = readLEB128()
        val type = readType()
        return (0 until n).map { type }
    }

    class Code(val locals: List<List<WasmType>>, val body: Expr) {
        val flatLocals get() = locals.flatMap { it }
    }

    fun SyncStream.readCode(): Code {
        val size = readLEB128()
        val ss = readBytesExact(size).openSync()
        val locals = ss.readVec { ss.readCodeLocals() }
        val expr = ss.readExpr()
        return Code(locals, expr)
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

    interface WasmType {
        val id: Int
        val signature: String

        object void : WasmType {
            override val id = 0
            override fun toString() = "void"
            override val signature: String = "v"
        }

        object i32 : WasmType {
            override val id = 1
            override fun toString() = "i32"
            override val signature: String = "i"
        }

        object i64 : WasmType {
            override val id = 2
            override fun toString() = "i64"
            override val signature: String = "l"
        }

        object f32 : WasmType {
            override val id = 3
            override fun toString() = "f32"
            override val signature: String = "f"
        }

        object f64 : WasmType {
            override val id = 4
            override fun toString() = "f64"
            override val signature: String = "d"
        }

        data class Limit(val min: Int, val max: Int?) : WasmType {
            override val id = -1
            override val signature: String = "l$min$max"
        }

        data class Function(val args: List<WasmType>, val ret: List<WasmType>) : WasmType {
            override val id = -1
            val retType get() = ret.firstOrNull() ?: WasmType.void
            val retTypeVoid get() = retType == WasmType.void
            val argsPlusRet get() = args + listOf(retType)
            override val signature: String get() = argsPlusRet.joinToString("") { it.signature }
        }

        data class Global(val type: WasmType, val mutable: Boolean) : WasmType {
            override val id = -1
            override val signature: String = "g${type.signature}${if (mutable) "m" else "i"}"
        }

        data class TableType(val limit: Limit)
    }

    fun SyncStream.readType(): WasmType {
        val type = readU8()
        //println("%02X".format(type))
        return when (type) {
            0x40 -> WasmType.void
            0x60 -> WasmType.Function(readVec { readType() }, readVec { readType() })
            0x7F -> WasmType.i32
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

    data class Import(val moduleName: String, val name: String, val indexSpace: Int, val index: Int, val type: Any)

    val INDEX_FUNCTIONS = 0
    val INDEX_TABLES = 1
    val INDEX_MEMTYPES = 2
    val INDEX_GLOBALS = 3
    val indicesInTables = arrayOf(0, 0, 0, 0)
    val importsInTables = arrayOf(0, 0, 0, 0)
    val importFunctionsOffset get() = importsInTables[INDEX_FUNCTIONS]
    //var functionIndex = 0

    fun SyncStream.readImport(): Import {
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
        val import = Import(moduleName, name, indexSpace, index, type)
        when (indexSpace) {
            INDEX_FUNCTIONS -> functions[index] =
                    WasmFunc(this@Wasm, index, type as WasmType.Function, code = null, import = import)
            INDEX_GLOBALS -> globals[index] = Global(type as WasmType.Global, index, e = null, import = import)
        }
        //println("$nm::$name = $type")
        return import
    }

    class Export(val name: String, val obj: Any?) {
        override fun toString(): String = "Export($name)"
    }

    fun SyncStream.readExportSection() {
        exports = readVec { readExport() }
        for ((index, export) in exports.withIndex()) {
            trace("// EXPORT[$index]: $export")
        }
    }

    fun SyncStream.readExport(): Export {
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
        val export = Export(name, obj)

        if (tid == 0) {
            functions[idx]?.export = export
        }

        //println("export[$name] = $tid[$idx] -- $obj")
        return export
    }

    data class WasmFunc(
        val wasm: Wasm,
        val index: Int,
        val type: WasmType.Function,
        var code: Code? = null,
        var import: Import? = null,
        var export: Export? = null
    ) {
        val rlocals by lazy { type.args + (code?.flatLocals ?: listOf()) }

        val name: String get() = import?.name ?: export?.name ?: "f$index"
    }

    fun SyncStream.readFunctionSection() {
        val funcs = readVec {
            val index = readLEB128()
            WasmFunc(this@Wasm, indicesInTables[INDEX_FUNCTIONS]++, types[index] as WasmType.Function)
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

    data class Element(val tableIdx: Int, val expr: Expr, val funcIdxs: List<Int>)

    fun SyncStream.readElement(): Element = Element(readTableIdx(), readExpr(), readVec { readFuncIdx() })

    fun SyncStream.readElementSection() {
        elements = readVec { readElement() }

        for ((index, e) in elements.withIndex()) {
            trace("// ELEMENT[$index]: $e")
        }
    }

    data class Global(
        val globalType: WasmType.Global,
        val index: Int,
        val e: Expr? = null,
        var import: Import? = null
    ) {
        val name get() = import?.name ?: "g$index"
    }

    fun SyncStream.readGlobal(): Global {
        val gt = readGlobalType()
        val e = readExpr()
        trace("// GLOBAL: $gt, $e")
        return Global(gt, indicesInTables[INDEX_GLOBALS]++, e)
    }

    interface Instruction {
        val op: Ops

        object end : Instruction {
            override val op: Ops = Ops.Op_end
        }

        object unreachable : Instruction {
            override val op: Ops = Ops.Op_unreachable
        }

        object nop : Instruction {
            override val op: Ops = Ops.Op_nop
        }

        data class block(val b: WasmType, val expr: Expr) : Instruction {
            override val op: Ops = Ops.Op_block
        }

        data class loop(val b: WasmType, val expr: Expr) : Instruction {
            override val op: Ops = Ops.Op_loop
        }

        data class IF(val bt: WasmType, val btrue: Expr, val bfalse: Expr?) : Instruction {
            override val op: Ops = Ops.Op_if
        }

        data class ELSE(val code: Expr) : Instruction {
            override val op: Ops = Ops.Op_else
        }

        data class br(val l: Int) : Instruction {
            override val op: Ops = Ops.Op_br
        }

        data class br_if(val l: Int) : Instruction {
            override val op: Ops = Ops.Op_br_if
        }

        data class br_table(val labels: List<Int>, val default: Int) : Instruction {
            override val op: Ops = Ops.Op_br_table
        }

        object RETURN : Instruction {
            override val op: Ops = Ops.Op_return
        }

        data class CALL(val funcIdx: Int) : Instruction {
            override val op: Ops = Ops.Op_call
        }

        data class CALL_INDIRECT(val typeIdx: Int, val zero: Int) : Instruction {
            override val op: Ops = Ops.Op_call_indirect
        }

        data class Ins(override val op: Ops) : Instruction
        data class InsInt(override val op: Ops, val param: Int) : Instruction
        data class InsConst(override val op: Ops, val param: Any) : Instruction {
            val paramInt get() = param as Int
            val paramLong get() = param as Long
            val paramFloat get() = param as Float
            val paramDouble get() = param as Double
        }

        data class InsMemarg(override val op: Ops, val align: Int, val offset: Int) : Instruction
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

    enum class Ops(
        val id: Int,
        val istack: Int = -1,
        val rstack: Int = -1,
        val symbol: String = "<?>",
        val outType: WasmType = WasmType.void
    ) {
        //Op_i32(0x7f),
        //Op_i64(0x7e),
        //Op_f32(0x7d),
        //Op_f64(0x7c),
        //Op_anyfunc(0x70),
        //Op_func(0x60),
        //Op_empty(0x40),


        // Control flow operators
        Op_unreachable(0x00),
        Op_nop(0x01),
        Op_block(0x02),
        Op_loop(0x03),
        Op_if(0x04),
        Op_else(0x05),
        Op_end(0x0b),
        Op_br(0x0c),
        Op_br_if(0x0d),
        Op_br_table(0x0e),
        Op_return(0x0f),

        // Call operators
        Op_call(0x10, -1),
        Op_call_indirect(0x11, -1),

        // Parametric operators
        Op_drop(0x1a, 1, 0),
        Op_select(0x1b, 3, 1),

        // Variable access
        Op_get_local(0x20),
        Op_set_local(0x21, 1),
        Op_tee_local(0x22),
        Op_get_global(0x23),
        Op_set_global(0x24, 1),


        // Memory-related operators
        Op_i32_load(0x28, 1, 1, outType = WasmType.i32),
        Op_i64_load(0x29, 1, 1, outType = WasmType.i64),
        Op_f32_load(0x2a, 1, 1, outType = WasmType.f32),
        Op_f64_load(0x2b, 1, 1, outType = WasmType.f64),

        Op_i32_load8_s(0x2c, 1, 1, outType = WasmType.i32),
        Op_i32_load8_u(0x2d, 1, 1, outType = WasmType.i32),
        Op_i32_load16_s(0x2e, 1, 1, outType = WasmType.i32),
        Op_i32_load16_u(0x2f, 1, 1, outType = WasmType.i32),

        Op_i64_load8_s(0x30, 1, 1, outType = WasmType.i64),
        Op_i64_load8_u(0x31, 1, 1, outType = WasmType.i64),
        Op_i64_load16_s(0x32, 1, 1, outType = WasmType.i64),
        Op_i64_load16_u(0x33, 1, 1, outType = WasmType.i64),
        Op_i64_load32_s(0x34, 1, 1, outType = WasmType.i64),
        Op_i64_load32_u(0x35, 1, 1, outType = WasmType.i64),

        Op_i32_store(0x36, 2, 0),
        Op_i64_store(0x37, 2, 0),
        Op_f32_store(0x38, 2, 0),
        Op_f64_store(0x39, 2, 0),
        Op_i32_store8(0x3a, 2, 0),
        Op_i32_store16(0x3b, 2, 0),
        Op_i64_store8(0x3c, 2, 0),
        Op_i64_store16(0x3d, 2, 0),
        Op_i64_store32(0x3e, 2, 0),

        Op_current_memory(0x3f),
        Op_grow_memory(0x40),

        // Constants opcodes
        Op_i32_const(0x41, 0, 1, outType = WasmType.i32),
        Op_i64_const(0x42, 0, 1, outType = WasmType.i64),
        Op_f32_const(0x43, 0, 1, outType = WasmType.f32),
        Op_f64_const(0x44, 0, 1, outType = WasmType.f64),

        // Comparison operators
        Op_i32_eqz(0x45, 1, 1, outType = WasmType.i32),
        Op_i32_eq(0x46, 2, 1, "==", outType = WasmType.i32),
        Op_i32_ne(0x47, 2, 1, "!=", outType = WasmType.i32),
        Op_i32_lt_s(0x48, 2, 1, "<", outType = WasmType.i32),
        Op_i32_lt_u(0x49, 2, 1, "<", outType = WasmType.i32), // @TODO
        Op_i32_gt_s(0x4a, 2, 1, ">", outType = WasmType.i32),
        Op_i32_gt_u(0x4b, 2, 1, ">", outType = WasmType.i32),
        Op_i32_le_s(0x4c, 2, 1, "<=", outType = WasmType.i32),
        Op_i32_le_u(0x4d, 2, 1, "<=", outType = WasmType.i32),
        Op_i32_ge_s(0x4e, 2, 1, ">=", outType = WasmType.i32),
        Op_i32_ge_u(0x4f, 2, 1, ">=", outType = WasmType.i32),
        Op_i64_eqz(0x50, 1, 1, outType = WasmType.i32),

        Op_i64_eq(0x51, 2, 1, "==", outType = WasmType.i32),
        Op_i64_ne(0x52, 2, 1, "!=", outType = WasmType.i32),
        Op_i64_lt_s(0x53, 2, 1, "<", outType = WasmType.i32),
        Op_i64_lt_u(0x54, 2, 1, "<", outType = WasmType.i32),
        Op_i64_gt_s(0x55, 2, 1, ">", outType = WasmType.i32),
        Op_i64_gt_u(0x56, 2, 1, ">", outType = WasmType.i32),
        Op_i64_le_s(0x57, 2, 1, "<=", outType = WasmType.i32),
        Op_i64_le_u(0x58, 2, 1, "<=", outType = WasmType.i32),
        Op_i64_ge_s(0x59, 2, 1, ">=", outType = WasmType.i32),
        Op_i64_ge_u(0x5a, 2, 1, ">=", outType = WasmType.i32),

        Op_f32_eq(0x5b, 2, 1, "==", outType = WasmType.i32),
        Op_f32_ne(0x5c, 2, 1, "!=", outType = WasmType.i32),
        Op_f32_lt(0x5d, 2, 1, "<", outType = WasmType.i32),
        Op_f32_gt(0x5e, 2, 1, ">", outType = WasmType.i32),
        Op_f32_le(0x5f, 2, 1, "<=", outType = WasmType.i32),
        Op_f32_ge(0x60, 2, 1, ">=", outType = WasmType.i32),

        Op_f64_eq(0x61, 2, 1, "==", outType = WasmType.i32),
        Op_f64_ne(0x62, 2, 1, "!=", outType = WasmType.i32),
        Op_f64_lt(0x63, 2, 1, "<", outType = WasmType.i32),
        Op_f64_gt(0x64, 2, 1, ">", outType = WasmType.i32),
        Op_f64_le(0x65, 2, 1, "<=", outType = WasmType.i32),
        Op_f64_ge(0x66, 2, 1, ">=", outType = WasmType.i32),


        // Numeric operators

        // int unary
        Op_i32_clz(0x67, 1, 1, outType = WasmType.i32),
        Op_i32_ctz(0x68, 1, 1, outType = WasmType.i32),
        Op_i32_popcnt(0x69, 1, 1, outType = WasmType.i32),

        // int binary
        Op_i32_add(0x6a, 2, 1, "+", outType = WasmType.i32),
        Op_i32_sub(0x6b, 2, 1, "-", outType = WasmType.i32),
        Op_i32_mul(0x6c, 2, 1, "*", outType = WasmType.i32),
        Op_i32_div_s(0x6d, 2, 1, "/", outType = WasmType.i32),
        Op_i32_div_u(0x6e, 2, 1, "/", outType = WasmType.i32), // @TODO
        Op_i32_rem_s(0x6f, 2, 1, "%", outType = WasmType.i32),
        Op_i32_rem_u(0x70, 2, 1, "%", outType = WasmType.i32),
        Op_i32_and(0x71, 2, 1, "&", outType = WasmType.i32),
        Op_i32_or(0x72, 2, 1, "|", outType = WasmType.i32),
        Op_i32_xor(0x73, 2, 1, "^", outType = WasmType.i32),
        Op_i32_shl(0x74, 2, 1, "<<", outType = WasmType.i32),
        Op_i32_shr_s(0x75, 2, 1, ">>", outType = WasmType.i32),
        Op_i32_shr_u(0x76, 2, 1, ">>>", outType = WasmType.i32),
        Op_i32_rotl(0x77, 2, 1, outType = WasmType.i32),
        Op_i32_rotr(0x78, 2, 1, outType = WasmType.i32),

        // long unary
        Op_i64_clz(0x79, 1, 1, outType = WasmType.i32),
        Op_i64_ctz(0x7a, 1, 1, outType = WasmType.i32),
        Op_i64_popcnt(0x7b, 1, 1, outType = WasmType.i32),

        // long binary
        Op_i64_add(0x7c, 2, 1, outType = WasmType.i64),
        Op_i64_sub(0x7d, 2, 1, outType = WasmType.i64),
        Op_i64_mul(0x7e, 2, 1, outType = WasmType.i64),
        Op_i64_div_s(0x7f, 2, 1, outType = WasmType.i64),
        Op_i64_div_u(0x80, 2, 1, outType = WasmType.i64),
        Op_i64_rem_s(0x81, 2, 1, outType = WasmType.i64),
        Op_i64_rem_u(0x82, 2, 1, outType = WasmType.i64),
        Op_i64_and(0x83, 2, 1, outType = WasmType.i64),
        Op_i64_or(0x84, 2, 1, outType = WasmType.i64),
        Op_i64_xor(0x85, 2, 1, outType = WasmType.i64),
        Op_i64_shl(0x86, 2, 1, outType = WasmType.i64),
        Op_i64_shr_s(0x87, 2, 1, outType = WasmType.i64),
        Op_i64_shr_u(0x88, 2, 1, outType = WasmType.i64),
        Op_i64_rotl(0x89, 2, 1, outType = WasmType.i64),
        Op_i64_rotr(0x8a, 2, 1, outType = WasmType.i64),

        // float unary
        Op_f32_abs(0x8b, 1, 1, outType = WasmType.f32),
        Op_f32_neg(0x8c, 1, 1, outType = WasmType.f32),
        Op_f32_ceil(0x8d, 1, 1, outType = WasmType.f32),
        Op_f32_floor(0x8e, 1, 1, outType = WasmType.f32),
        Op_f32_trunc(0x8f, 1, 1, outType = WasmType.f32),
        Op_f32_nearest(0x90, 1, 1, outType = WasmType.f32),
        Op_f32_sqrt(0x91, 1, 1, outType = WasmType.f32),

        // float binary
        Op_f32_add(0x92, 2, 1, outType = WasmType.f32),
        Op_f32_sub(0x93, 2, 1, outType = WasmType.f32),
        Op_f32_mul(0x94, 2, 1, outType = WasmType.f32),
        Op_f32_div(0x95, 2, 1, outType = WasmType.f32),
        Op_f32_min(0x96, 2, 1, outType = WasmType.f32),
        Op_f32_max(0x97, 2, 1, outType = WasmType.f32),
        Op_f32_copysign(0x98, 2, 1, outType = WasmType.f32),

        // double unary
        Op_f64_abs(0x99, 1, 1, outType = WasmType.f64),
        Op_f64_neg(0x9a, 1, 1, outType = WasmType.f64),
        Op_f64_ceil(0x9b, 1, 1, outType = WasmType.f64),
        Op_f64_floor(0x9c, 1, 1, outType = WasmType.f64),
        Op_f64_trunc(0x9d, 1, 1, outType = WasmType.f64),
        Op_f64_nearest(0x9e, 1, 1, outType = WasmType.f64),
        Op_f64_sqrt(0x9f, 1, 1, outType = WasmType.f64),

        // double binary
        Op_f64_add(0xa0, 2, 1, outType = WasmType.f64),
        Op_f64_sub(0xa1, 2, 1, outType = WasmType.f64),
        Op_f64_mul(0xa2, 2, 1, outType = WasmType.f64),
        Op_f64_div(0xa3, 2, 1, outType = WasmType.f64),
        Op_f64_min(0xa4, 2, 1, outType = WasmType.f64),
        Op_f64_max(0xa5, 2, 1, outType = WasmType.f64),
        Op_f64_copysign(0xa6, 2, 1, outType = WasmType.f64),

        // Conversions
        Op_i32_wrap_i64(0xa7, 1, 1, outType = WasmType.i32),
        Op_i32_trunc_s_f32(0xa8, 1, 1, outType = WasmType.i32),
        Op_i32_trunc_u_f32(0xa9, 1, 1, outType = WasmType.i32),
        Op_i32_trunc_s_f64(0xaa, 1, 1, outType = WasmType.i32),
        Op_i32_trunc_u_f64(0xab, 1, 1, outType = WasmType.i32),

        Op_i64_extend_s_i32(0xac, 1, 1, outType = WasmType.i64),
        Op_i64_extend_u_i32(0xad, 1, 1, outType = WasmType.i64),
        Op_i64_trunc_s_f32(0xae, 1, 1, outType = WasmType.i64),
        Op_i64_trunc_u_f32(0xaf, 1, 1, outType = WasmType.i64),
        Op_i64_trunc_s_f64(0xb0, 1, 1, outType = WasmType.i64),
        Op_i64_trunc_u_f64(0xb1, 1, 1, outType = WasmType.i64),

        Op_f32_convert_s_i32(0xb2, 1, 1, outType = WasmType.f32),
        Op_f32_convert_u_i32(0xb3, 1, 1, outType = WasmType.f32),
        Op_f32_convert_s_i64(0xb4, 1, 1, outType = WasmType.f32),
        Op_f32_convert_u_i64(0xb5, 1, 1, outType = WasmType.f32),
        Op_f32_demote_f64(0xb6, 1, 1, outType = WasmType.f32),

        Op_f64_convert_s_i32(0xb7, 1, 1, outType = WasmType.f64),
        Op_f64_convert_u_i32(0xb8, 1, 1, outType = WasmType.f64),
        Op_f64_convert_s_i64(0xb9, 1, 1, outType = WasmType.f64),
        Op_f64_convert_u_i64(0xba, 1, 1, outType = WasmType.f64),
        Op_f64_promote_f32(0xbb, 1, 1, outType = WasmType.f64),

        // Reinterpretations
        Op_i32_reinterpret_f32(0xbc, 1, 1, outType = WasmType.i32),
        Op_i64_reinterpret_f64(0xbd, 1, 1, outType = WasmType.i64),
        Op_f32_reinterpret_i32(0xbe, 1, 1, outType = WasmType.f32),
        Op_f64_reinterpret_i64(0xbf, 1, 1, outType = WasmType.f64);

        companion object {
            val OPS_BY_ID = values().associateBy { it.id }
            operator fun get(index: Int): Ops = OPS_BY_ID[index] ?: invalidOp("Invalid OP $index")
        }
    }

    data class Expr(val ins: List<Instruction>)

    fun SyncStream.readExpr(): Expr {
        val seq = arrayListOf<Instruction>()
        while (true) {
            val i = readInstr()
            //println("i: $i")
            seq += i
            if (i == Instruction.end || i is Instruction.ELSE) break
        }
        //println("----------")
        return Expr(seq)
    }

    fun SyncStream.peekU8() = keepPosition { readU8() }

    fun <T> SyncStream.readVec(callback: (Int) -> T): List<T> {
        return (0 until readLEB128()).map { callback(it) }
    }

    fun SyncStream.readInstr(): Instruction {
        //println("%08X: ${position.toInt().hex}")
        val op = readU8()
        val oop = Ops[op]

        val i = when (op) {
            0x00 -> Instruction.unreachable
            0x01 -> Instruction.nop
            0x02 -> Instruction.block(readBlockType(), readExpr())
            0x03 -> Instruction.loop(readBlockType(), readExpr())
            0x04 -> {
                val bt = readBlockType()
                val in1 = readExpr()
                if (in1.ins.lastOrNull() is Instruction.ELSE) {
                    val _else = in1.ins.last() as Instruction.ELSE
                    Instruction.IF(bt, Expr(in1.ins.dropLast(1)), _else.code)
                } else {
                    Instruction.IF(bt, in1, null)
                }
            }
            0x05 -> Instruction.ELSE(readExpr())
            0x0B -> Instruction.end
            0x0C -> Instruction.br(readLEB128())
            0x0D -> Instruction.br_if(readLEB128())
            0x0E -> Instruction.br_table(readVec { readLEB128() }, readLEB128())
            0x0F -> Instruction.RETURN
            0x10 -> Instruction.CALL(readLEB128())
            0x11 -> Instruction.CALL_INDIRECT(readLEB128(), readU8())
            0x1A, 0x1B -> Instruction.Ins(Ops[op])
            in 0x20..0x24 -> Instruction.InsInt(oop, readLEB128())
            in 0x28..0x3E -> Instruction.InsMemarg(oop, readLEB128(), readLEB128())
            0x3F, 0x40 -> Instruction.InsInt(oop, readU8())
            0x41 -> Instruction.InsConst(oop, readLEB128S())
            0x42 -> Instruction.InsConst(oop, readLEB128SLong())
            0x43 -> Instruction.InsConst(oop, readF32_le())
            0x44 -> Instruction.InsConst(oop, readF64_le())
            in 0x45..0xBf -> Instruction.Ins(Ops[op])
            else -> invalidOp("Unsupported 0x%02X".format(op))
        }

        //println(" ---> $op: $oop [$i]")
        return i
    }
}

val INT_FUNC_TYPE = Wasm.WasmType.Function(listOf(), listOf(Wasm.WasmType.i32))

