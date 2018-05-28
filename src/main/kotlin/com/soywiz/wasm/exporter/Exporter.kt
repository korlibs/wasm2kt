package com.soywiz.wasm.exporter

import com.soywiz.korio.error.*
import com.soywiz.korio.util.*
import com.soywiz.wasm.*
import com.soywiz.wasm.Indenter
import java.util.*

data class ExportConfig(
    val className: String,
    val packageName: String = ""
)

open class Exporter(val module: WasmModule) {
    companion object {
        val JAVA_KEYWORDS = setOf("do", "while", "if", "else", "void", "int", "this") // ...
        val PHI_NAMES = setOf("phi_i32", "phi_i64", "phi_f32", "phi_f64", "java")
        val RESERVED_LOCALS = setOf("index")
        val JAVA_DEFINED_NAMES: Set<String> = JAVA_KEYWORDS + PHI_NAMES + RESERVED_LOCALS

        val O_RDONLY = 0x0000
        val O_WRONLY = 0x0001
        val O_RDWR = 0x0002
        val O_CREAT = 0x40
        val O_EXCL = 0x80
        val O_TRUNC = 0x200
        val O_APPEND = 0x400
        val O_DSYNC = 0x1000

        val void = WasmType.void
        val i8 = WasmType._i8
        val i16 = WasmType._i16
        val i32 = WasmType.i32
        val i64 = WasmType.i64
        val f32 = WasmType.f32
        val f64 = WasmType.f64

        fun type(name: String) = object : WasmType {
            override val id: Int = -1
            override val signature: String = "?"
            override fun toString() = name
        }
        fun arrayType(type: WasmType) = WasmType._ARRAY(type)
        val BA = arrayType(WasmType._i8)
        val IA = arrayType(WasmType.i32)
        val bool = WasmType._boolean
        val int = WasmType.i32
        val long = WasmType.i64
        val float = WasmType.f32
        val double = WasmType.f64
    }

    open val DEFINED_NAMES = JAVA_DEFINED_NAMES

    open fun langNameAllocator() = NameAllocator {
        //var res = it.replace('$', '_').replace('-', '_')
        var res = it.replace('-', '_')
        while (res in DEFINED_NAMES) res = "_$res"
        res
    }


    val moduleCtx = ModuleDumpContext()
    val functionsWithImport = module.functions.filter { it.import != null }
    val functionsByImport = functionsWithImport.map { it.import!!.importPair to it }.toMap()
    val handledFunctionsWithImport = LinkedHashSet<WasmFunc>()

    val globalsWithImport = module.globals.filter { it.import != null }
    val globalsByImport = globalsWithImport.map { it.import!!.importPair to it }.toMap()

    val handledGlobalsWithImport = LinkedHashSet<Wasm.WasmGlobal>()

    val STACKTOP by lazy {getImportGlobal("env", "STACKTOP") ?: "STACKTOP" }
    val STACK_MAX by lazy {getImportGlobal("env", "STACK_MAX") ?: "STACK_MAX" }
    val DYNAMICTOP_PTR by lazy {getImportGlobal("env", "DYNAMICTOP_PTR") ?: "DYNAMICTOP_PTR" }
    val tempDoublePtr by lazy {getImportGlobal("env", "tempDoublePtr") ?: "tempDoublePtr" }

    open val String.asize get() = "$this.length"
    val String.cbyte get() = cast(i8, this)
    val String.cshort get() = cast(i16, this)
    val String.cint get() = cast(i32, this)
    val String.clong get() = cast(i64, this)
    val String.cfloat get() = cast(f32, this)
    val String.cdouble get() = cast(f64, this)

    inline fun Indenter.unop_float(op: WasmOp, expr: () -> String) = unop(op, f32, f32, expr())
    inline fun Indenter.unop_double(op: WasmOp, expr: () -> String) = unop(op, f64, f64, expr())

    inline fun Indenter.binop_int_bool(op: WasmOp, expr: () -> String) = binop(op, i32, i32, "b2i(${expr()})")
    inline fun Indenter.binop_int(op: WasmOp, expr: () -> String) = binop(op, i32, i32, expr())

    inline fun Indenter.binop_long_bool(op: WasmOp, expr: () -> String) = binop(op, i64, i32, "b2i(${expr()})")
    inline fun Indenter.binop_long(op: WasmOp, expr: () -> String) = binop(op, i64, i64, expr())

    inline fun Indenter.binop_float_bool(op: WasmOp, expr: () -> String) = binop(op, f32, i32, "b2i(${expr()})")
    inline fun Indenter.binop_float(op: WasmOp, expr: () -> String) = binop(op, f32, f32, expr())

    inline fun Indenter.binop_double_bool(op: WasmOp, expr: () -> String) = binop(op, f64, i32, "b2i(${expr()})")
    inline fun Indenter.binop_double(op: WasmOp, expr: () -> String) = binop(op, f64, f64, expr())

    inline fun Indenter.unop(op: WasmOp, arg: WasmType, ret: WasmType, expr: () -> String) = unop(op, arg, ret, expr())

    open fun Indenter.binop(op: WasmOp, arg: WasmType, ret: WasmType, expr: String) = FUNC("$op", ret, arg("l"), arg("r")) { expr }
    open fun Indenter.unop(op: WasmOp, arg: WasmType, ret: WasmType, expr: String) = FUNC("$op", ret, arg("v")) { expr }

    open fun ternary(cond: String, strue: String, sfalse: String) = "(($cond) ? ($strue) : ($sfalse))"
    open fun cast(type: WasmType, expr: String) = "((${type.type()})($expr))"
    open val AND = "&"
    open val OR = "|"
    open val XOR = "^"
    open val SHL = "<<"
    open val SHR = ">>"
    open val SHRU = ">>>"

    protected open fun Indenter.writeImportedFuncs() {
        iglob("global", "NaN", double) { "java.lang.Double.NaN" }
        iglob("global", "Infinity", double) { "java.lang.Double.POSITIVE_INFINITY" }
        ifunc("global.Math", "pow", double, double("a"), double("b")) { line("return java.lang.Math.pow(a, b);") }

        iglob("env", "tableBase", int) { "0" }
        iglob("env", "ABORT", int) { "-1" }
        ifunc("env", "getTotalMemory", int) { line("""return heap.limit();""") }
        ifunc("env", "enlargeMemory", int) { line("""return TODO_i32("enlargeMemory");""") }
        ifunc("env", "abortOnCannotGrowMemory", void) { line("""TODO("abortOnCannotGrowMemory");""") }
        ifunc("env", "abortStackOverflow", void, int("arg")) { line("""TODO("abortStackOverflow");""") }
        ifunc("env", "abort", void, int("v")) { line("""TODO("ABORT: " + v);""") }
        ifunc("env", "_abort", void) { line("TODO(\"ABORT\");") }
        ifunc("env", "nullFunc_i", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_ii", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_iii", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_iiii", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_v", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_vi", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_vii", int, int("v")) { line("return 0;") }
        ifunc("env", "nullFunc_viii", int, int("v")) { line("return 0;") }
        ifunc("env", "___assert_fail", void, int("a"), int("b"), int("c"), int("d")) {
            line("TODO(\"___assert_fail\");")
        }
        ifunc("env", "___lock", void, int("addr")) { line("") }
        ifunc("env", "___unlock", void, int("addr")) { line("") }
        ifunc("env", "_emscripten_memcpy_big", int, int("dst"), int("src"), int("count")) {
            line("System.arraycopy(heapBytes, src, heapBytes, dst, count);")
            line("return dst;")
        }
        ifunc("env", "___setErrNo", void, int("error")) { line("") }
        ifunc("env", "_exit", void, int("code")) { line("System.exit(code);") }
        ifunc("env", "_time", int, int("addr")) {
            line("int time = (int)(System.currentTimeMillis() / 1000L);")
            line("if (addr != 0) sw(addr, time);")
            line("return time;")
        }

        for (func in functionsWithImport - handledFunctionsWithImport) {
            System.err.println("Unimported function ${func.import}")
        }
    }

    protected open fun Indenter.writeBaseOps() {
        iopr(WasmOp.Op_i32_load, int) { "heap.getInt(checkARead(address, offset, align, 4))" }
        iopr(WasmOp.Op_i64_load, long) { "heap.getLong(checkARead(address, offset, align, 8))" }
        iopr(WasmOp.Op_f32_load, float) { "heap.getFloat(checkARead(address, offset, align, 4))" }
        iopr(WasmOp.Op_f64_load, double) { "heap.getDouble(checkARead(address, offset, align, 8))" }

        iopr(WasmOp.Op_i32_load8_s, int) { "heap.get(checkARead(address, offset, align, 1))".cint }
        iopr(WasmOp.Op_i32_load8_u, int) { "heap.get(checkARead(address, offset, align, 1)) $AND 0xFF".cint }
        iopr(WasmOp.Op_i32_load16_s, int) { "heap.getShort(checkARead(address, offset, align, 2))".cint }
        iopr(WasmOp.Op_i32_load16_u, int) { "heap.getShort(checkARead(address, offset, align, 2)) $AND 0xFFFF".cint }
        iopr(WasmOp.Op_i64_load8_s, long) { "heap.get(checkARead(address, offset, align, 1))".clong }
        iopr(WasmOp.Op_i64_load8_u, long) { "heap.get(checkARead(address, offset, align, 1)) $AND 0xFFL".clong }
        iopr(WasmOp.Op_i64_load16_s, long) { "heap.getShort(checkARead(address, offset, align, 2))".clong }
        iopr(WasmOp.Op_i64_load16_u, long) { "heap.getShort(checkARead(address, offset, align, 2)) $AND 0xFFFFL".clong }
        iopr(WasmOp.Op_i64_load32_s, long) { "heap.getInt(checkARead(address, offset, align, 4))".clong }
        iopr(WasmOp.Op_i64_load32_u, long) { "heap.getInt(checkARead(address, offset, align, 4)) $AND 0xFFFFFFFFL".clong }

        iopw(WasmOp.Op_i32_store8, int) { "heap.put(checkAWrite(address, offset, align, 1), ${"value".cbyte});" }
        iopw(WasmOp.Op_i32_store16, int) { "heap.putShort(checkAWrite(address, offset, align, 2), ${"value".cshort});" }
        iopw(WasmOp.Op_i32_store, int) { "heap.putInt(checkAWrite(address, offset, align, 4), value);" }
        iopw(WasmOp.Op_i64_store, long) { "heap.putLong(checkAWrite(address, offset, align, 8), value);" }
        iopw(WasmOp.Op_f32_store, float) { "heap.putFloat(checkAWrite(address, offset, align, 4), value);" }
        iopw(WasmOp.Op_f64_store, double) { "heap.putDouble(checkAWrite(address, offset, align, 8), value);" }
        iopw(WasmOp.Op_i64_store8, long) { "Op_i32_store8(address, offset, align, ${"value".cint});" }
        iopw(WasmOp.Op_i64_store16, long) { "Op_i32_store16(address, offset, align, ${"value".cint});" }
        iopw(WasmOp.Op_i64_store32, long) { "Op_i32_store(address, offset, align, ${"value".cint});" }

        unop(WasmOp.Op_i32_eqz, i32, i32) { "b2i(v == 0)" }
        unop(WasmOp.Op_i64_eqz, i64, i32) { "b2i(v == 0L)" }

        binop_int_bool(WasmOp.Op_i32_eq) { "l == r" }
        binop_int_bool(WasmOp.Op_i32_ne) { "l != r" }
        binop_int_bool(WasmOp.Op_i32_lt_s) { "l < r" }
        binop_int_bool(WasmOp.Op_i32_lt_u) { "java.lang.Integer.compareUnsigned(l, r) < 0" }
        binop_int_bool(WasmOp.Op_i32_gt_s) { "l > r" }
        binop_int_bool(WasmOp.Op_i32_gt_u) { "java.lang.Integer.compareUnsigned(l, r) > 0" }
        binop_int_bool(WasmOp.Op_i32_le_s) { "l <= r" }
        binop_int_bool(WasmOp.Op_i32_le_u) { "java.lang.Integer.compareUnsigned(l, r) <= 0" }
        binop_int_bool(WasmOp.Op_i32_ge_s) { "l >= r" }
        binop_int_bool(WasmOp.Op_i32_ge_u) { "java.lang.Integer.compareUnsigned(l, r) >= 0" }

        binop_long_bool(WasmOp.Op_i64_eq) { "l == r" }
        binop_long_bool(WasmOp.Op_i64_ne) { "l != r" }
        binop_long_bool(WasmOp.Op_i64_lt_s) { "l < r" }
        binop_long_bool(WasmOp.Op_i64_lt_u) { "java.lang.Long.compareUnsigned(l, r) < 0" }
        binop_long_bool(WasmOp.Op_i64_gt_s) { "l > r" }
        binop_long_bool(WasmOp.Op_i64_gt_u) { "java.lang.Long.compareUnsigned(l, r) > 0" }
        binop_long_bool(WasmOp.Op_i64_le_s) { "l <= r" }
        binop_long_bool(WasmOp.Op_i64_le_u) { "java.lang.Long.compareUnsigned(l, r) <= 0" }
        binop_long_bool(WasmOp.Op_i64_ge_s) { "l >= r" }
        binop_long_bool(WasmOp.Op_i64_ge_u) { "java.lang.Long.compareUnsigned(l, r) >= 0" }

        binop_float_bool(WasmOp.Op_f32_eq) { "l == r" }
        binop_float_bool(WasmOp.Op_f32_ne) { "l != r" }
        binop_float_bool(WasmOp.Op_f32_lt) { "l < r" }
        binop_float_bool(WasmOp.Op_f32_gt) { "l > r" }
        binop_float_bool(WasmOp.Op_f32_le) { "l <= r" }
        binop_float_bool(WasmOp.Op_f32_ge) { "l >= r" }

        binop_double_bool(WasmOp.Op_f64_eq) { "l == r" }
        binop_double_bool(WasmOp.Op_f64_ne) { "l != r" }
        binop_double_bool(WasmOp.Op_f64_lt) { "l < r" }
        binop_double_bool(WasmOp.Op_f64_gt) { "l > r" }
        binop_double_bool(WasmOp.Op_f64_le) { "l <= r" }
        binop_double_bool(WasmOp.Op_f64_ge) { "l >= r" }

        unop(WasmOp.Op_i32_clz, i32, i32) { "java.lang.Integer.numberOfLeadingZeros(v)" }
        unop(WasmOp.Op_i32_ctz, i32, i32) { "java.lang.Integer.numberOfTrailingZeros(v)" }
        unop(WasmOp.Op_i32_popcnt, i32, i32) { "java.lang.Integer.bitCount(v)" }

        binop_int(WasmOp.Op_i32_add) { "l + r" }
        binop_int(WasmOp.Op_i32_sub) { "l - r" }
        binop_int(WasmOp.Op_i32_mul) { "l * r" }
        binop_int(WasmOp.Op_i32_div_s) { "l / r" }
        binop_int(WasmOp.Op_i32_div_u) { "java.lang.Integer.divideUnsigned(l, r)" }
        binop_int(WasmOp.Op_i32_rem_s) { "l % r" }
        binop_int(WasmOp.Op_i32_rem_u) { "java.lang.Integer.remainderUnsigned(l, r)" }

        binop_int(WasmOp.Op_i32_and) { "l $AND r" }
        binop_int(WasmOp.Op_i32_or) { "l $OR r" }
        binop_int(WasmOp.Op_i32_xor) { "l $XOR r" }
        binop_int(WasmOp.Op_i32_shl) { "l $SHL r" }
        binop_int(WasmOp.Op_i32_shr_s) { "l $SHR r" }
        binop_int(WasmOp.Op_i32_shr_u) { "l $SHRU r" }
        binop_int(WasmOp.Op_i32_rotl) { "java.lang.Integer.rotateLeft(l, r)" }
        binop_int(WasmOp.Op_i32_rotr) { "java.lang.Integer.rotateRight(l, r)" }

        unop(WasmOp.Op_i64_clz, i64, i32) { "java.lang.Long.numberOfLeadingZeros(v)" }
        unop(WasmOp.Op_i64_ctz, i64, i32) { "java.lang.Long.numberOfTrailingZeros(v)" }
        unop(WasmOp.Op_i64_popcnt, i64, i32) { "java.lang.Long.bitCount(v)" }

        binop_long(WasmOp.Op_i64_add) { "l + r" }
        binop_long(WasmOp.Op_i64_sub) { "l - r" }
        binop_long(WasmOp.Op_i64_mul) { "l * r" }
        binop_long(WasmOp.Op_i64_div_s) { "l / r" }
        binop_long(WasmOp.Op_i64_div_u) { "java.lang.Long.divideUnsigned(l, r)" }
        binop_long(WasmOp.Op_i64_rem_s) { "l % r" }
        binop_long(WasmOp.Op_i64_rem_u) { "java.lang.Long.remainderUnsigned(l, r)" }
        binop_long(WasmOp.Op_i64_and) { "l $AND r" }
        binop_long(WasmOp.Op_i64_or) { "l $OR r" }
        binop_long(WasmOp.Op_i64_xor) { "l $XOR r" }
        binop_long(WasmOp.Op_i64_shl) { "l $SHL r" }
        binop_long(WasmOp.Op_i64_shr_s) { "l $SHR r" }
        binop_long(WasmOp.Op_i64_shr_u) { "l $SHRU r" }
        binop_long(WasmOp.Op_i64_rotl) { "java.lang.Long.rotateLeft(l, ${"r".cint})" }
        binop_long(WasmOp.Op_i64_rotr) { "java.lang.Long.rotateRight(l, ${"r".cint})" }

        unop_float(WasmOp.Op_f32_abs) { "java.lang.Math.abs(v)" }
        unop_float(WasmOp.Op_f32_neg) { "-v" }
        unop_float(WasmOp.Op_f32_ceil) { "java.lang.Math.ceil(${"v".cdouble})".cfloat }
        unop_float(WasmOp.Op_f32_floor) { "java.lang.Math.floor(${"v".cdouble})".cfloat }
        unop_float(WasmOp.Op_f32_trunc) { "v".clong.cfloat } // @TODO: TODO
        unop_float(WasmOp.Op_f32_nearest) { "java.lang.Math.round(${"v".cdouble})".cfloat } // @TODO: TODO
        unop_float(WasmOp.Op_f32_sqrt) { "java.lang.Math.sqrt(${"v".cdouble})".cfloat }

        binop_float(WasmOp.Op_f32_add) { "l + r" }
        binop_float(WasmOp.Op_f32_sub) { "l - r" }
        binop_float(WasmOp.Op_f32_mul) { "l * r" }
        binop_float(WasmOp.Op_f32_div) { "l / r" }
        binop_float(WasmOp.Op_f32_min) { "java.lang.Math.min(l, r)" }
        binop_float(WasmOp.Op_f32_max) { "java.lang.Math.max(l, r)" }
        binop_float(WasmOp.Op_f32_copysign) { "java.lang.Math.copySign(l, r)" }

        unop_double(WasmOp.Op_f64_abs) { "java.lang.Math.abs(v)" }
        unop_double(WasmOp.Op_f64_neg) { "-v" }
        unop_double(WasmOp.Op_f64_ceil) { "java.lang.Math.ceil(v)" }
        unop_double(WasmOp.Op_f64_floor) { "java.lang.Math.floor(v)" }
        unop_double(WasmOp.Op_f64_trunc) { "v".clong.cdouble } // @TODO: TODO
        unop_double(WasmOp.Op_f64_nearest) { "java.lang.Math.round(v)" } // @TODO: TODO
        unop_double(WasmOp.Op_f64_sqrt) { "java.lang.Math.sqrt(v)" }

        binop_double(WasmOp.Op_f64_add) { "l + r" }
        binop_double(WasmOp.Op_f64_sub) { "l - r" }
        binop_double(WasmOp.Op_f64_mul) { "l * r" }
        binop_double(WasmOp.Op_f64_div) { "l / r" }
        binop_double(WasmOp.Op_f64_min) { "java.lang.Math.min(l, r)" }
        binop_double(WasmOp.Op_f64_max) { "java.lang.Math.max(l, r)" }
        binop_double(WasmOp.Op_f64_copysign) { "java.lang.Math.copySign(l, r)" }

        unop(WasmOp.Op_i32_wrap_i64, i64, i32, "(v & 0xFFFFFFFFL)".cint)
        unop(WasmOp.Op_i32_trunc_s_f32, f32, i32, "v".cint) // @TODO: VERIFY!
        unop(WasmOp.Op_i32_trunc_u_f32, f32, i32, "v".clong.cint) // @TODO: VERIFY!

        unop(WasmOp.Op_i32_trunc_s_f64, f64, i32) {
            ternary(
                "v <= ${"java.lang.Integer.MIN_VALUE".cdouble}",
                "java.lang.Integer.MIN_VALUE",
                ternary(
                    "v >= ${"java.lang.Integer.MAX_VALUE".cdouble}",
                    "java.lang.Integer.MAX_VALUE",
                    "v".cint
                )
            )
        }
        unop(WasmOp.Op_i32_trunc_u_f64, f64, i32) {
            ternary(
                "v <= 0.0",
                "0",
                ternary("v >= 4294967296.0", "4294967296L".cint, "v".cint)
            )
        }
        unop(WasmOp.Op_i64_extend_s_i32, i32, i64) { "v".clong }
        unop(WasmOp.Op_i64_extend_u_i32, i32, i64) { "${"v".clong} $AND 0xFFFFFFFFL" }

        unop(WasmOp.Op_i64_trunc_s_f32, f32, i64) { "v".clong } // @TODO: FIXME!
        unop(WasmOp.Op_i64_trunc_u_f32, f32, i64) { "v".clong } // @TODO: FIXME!

        unop(WasmOp.Op_i64_trunc_s_f64, f64, i64) { "v".clong } // @TODO: FIXME!
        unop(WasmOp.Op_i64_trunc_u_f64, f64, i64) { "v".clong } // @TODO: FIXME!

        unop(WasmOp.Op_f32_convert_s_i32, i32, f32) { "v".cfloat } // @TODO: FIXME!
        unop(
            WasmOp.Op_f32_convert_u_i32,
            i32,
            f32
        ) { "${"v".clong} $AND 0xFFFFFFFFL".cfloat } // @TODO: FIXME!
        unop(WasmOp.Op_f32_convert_s_i64, i64, f32) { "v".cfloat } // @TODO: FIXME!
        unop(WasmOp.Op_f32_convert_u_i64, i64, f32) { "v".cfloat } // @TODO: FIXME!

        unop(WasmOp.Op_f32_demote_f64, f64, f32) { "v".cfloat }
        unop(WasmOp.Op_f64_promote_f32, f32, f64) { "v".cdouble }

        unop(WasmOp.Op_f64_convert_s_i32, i32, f64) { "v".cdouble }
        unop(WasmOp.Op_f64_convert_u_i32, i32, f64) { "${"v".clong} $AND 0xFFFFFFFFL".cdouble }
        unop(WasmOp.Op_f64_convert_s_i64, i64, f64) { "v".cdouble }
        unop(WasmOp.Op_f64_convert_u_i64, i64, f64) { "v".cdouble } // @TODO: FIXME!

        unop(WasmOp.Op_i32_reinterpret_f32, f32, i32) { "java.lang.Float.floatToRawIntBits(v)" }
        unop(WasmOp.Op_i64_reinterpret_f64, f64, i64) { "java.lang.Double.doubleToRawLongBits(v)" }
        unop(WasmOp.Op_f32_reinterpret_i32, i32, f32) { "java.lang.Float.intBitsToFloat(v)" }
        unop(WasmOp.Op_f64_reinterpret_i64, i64, f64) { "java.lang.Double.longBitsToDouble(v)" }
    }

    fun Indenter.iopw(op: WasmOp, type: WasmType, gen: () -> String) =
        iop(op, void, int("address"), int("offset"), int("align"), type("value")) { gen() }

    fun Indenter.iopr(op: WasmOp, type: WasmType, gen: () -> String) =
        iop(op, type, int("address"), int("offset"), int("align")) { gen() }

    fun Indenter.iop(op: WasmOp, ret: WasmType, vararg args: MyArg, expr: () -> String) = FUNC("$op", ret, *args) { expr() }

    protected open fun Indenter.writeMain(className: String) {
        val mainFunc = module.functions.firstOrNull { it.export?.name == "_main" }
        if (mainFunc != null) {
            val funcName = moduleCtx.getName(mainFunc)
            line("static public void main(String[] args)") {
                line("$className module = new $className();")
                line("int result = 0;")
                //line("for (int m = 0; m < 100; m++)") {
                run {
                    val mainArity = mainFunc.type.args.size
                    when (mainArity) {
                        0 -> line("result = module.$funcName();")
                        2 -> {
                            line("int[] array = new int[args.length + 1];")
                            line("array[0] = module.allocStringz(\"$className\");")
                            line("for (int n = 1; n < array.length; n++)") {
                                line("array[n] = module.allocStringz(args[n - 1]);")
                            }
                            line("result = module.$funcName(array.length, module.allocInts(array));")
                        }
                        else -> invalidOp("Invalid function main with arity $mainArity")
                    }
                }
                line("System.exit(result);")
            }
        }
    }

    fun Indenter.iglob(ns: String, name: String, ret: WasmType, callback: () -> String) {
        getImportGlobal(ns, name)?.let {
            line("${ret.type()} $it = ${callback()};")
        }
    }

    data class MyArg(val name: String, val type: WasmType)

    operator fun WasmType.invoke(name: String) = MyArg(name, this)

    open fun Array<out MyArg>.argstr() = this.joinToString(", ") { it.type.type() + " " + it.name }

    fun Indenter.ifunc(ns: String, name: String, ret: WasmType, vararg args: MyArg, callback: Indenter.() -> Unit) {
        getImportFunc(ns, name)?.let {
            line("${ret.type()} $it(${args.argstr()})") {
                callback()
            }
        }
    }

    protected open fun Indenter.syscall(syscall: WasmSyscall, handler: Indenter.() -> Unit) {
        getImportFunc("env", "___syscall${syscall.id}")?.let {
            line("private int $it(int syscall, int address)") {
                line("try") {
                    handler()
                }
                line("catch (Throwable e)") {
                    line("throw new RuntimeException(e);")
                }
            }
        }
    }

    fun Indenter.missingSyscalls() {
        for (func in functionsWithImport) {
            val import = func.import ?: continue
            if (import.moduleName == "env" && import.name.startsWith("___syscall") && func !in handledFunctionsWithImport) {
                val syscallId = import.name.removePrefix("___syscall").toInt()
                val syscall = WasmSyscall.SYSCALL_BY_ID[syscallId] ?: continue
                syscall(syscall) {
                    line("return TODO_i32(\"unimplemented syscall $syscallId\");")
                }
            }
        }
    }

    fun getImportFunc(ns: String, name: String): String? {
        val import = Pair(ns, name)
        val func = functionsByImport[import]
        return if (func != null) {
            handledFunctionsWithImport += func
            moduleCtx.getName(func)
        } else {
            null
        }
    }

    fun getImportGlobal(ns: String, name: String): String? {
        val import = Pair(ns, name)
        val global = globalsByImport[import]
        return if (global != null) {
            handledGlobalsWithImport += global
            moduleCtx.getName(global)
        } else {
            null
        }
    }

    val initBlockSize = 16

    val names by lazy { langNameAllocator() }

    open fun Indenter.dumpConstructor(className: String) {
        line("public $className()") {
            for (nn in 0 until module.datas.size step initBlockSize) line("init$nn();")
            line("initDynamictop();")
        }
    }

    open fun Indenter.statics(callback: Indenter.() -> Unit) {
        callback()
    }

    open fun Indenter.memoryStatics() {
        line("static public final int HEAP_SIZE = 64 * 1024 * 1024; // 64 MB")
        line("static public final int STACK_INITIAL_SIZE = 128 * 1024; // 128 KB ")
    }

    open fun Indenter.memoryDynamics() {
        line("public final byte[] heapBytes = new byte[HEAP_SIZE];")
        line("public final java.nio.ByteBuffer heap = java.nio.ByteBuffer.wrap(heapBytes).order(java.nio.ByteOrder.nativeOrder());")
    }

    open fun dump(config: ExportConfig): Indenter = Indenter {
        val className = names.allocate(config.className)
        if (config.packageName.isNotEmpty()) {
            line("package ${config.packageName};")
        }
        line("public final class $className") {
            dumpConstructor(className)
            statics {
                writeMain(className)
                memoryStatics()
            }
            memoryDynamics()

            var maxMem = 1024
            val dataIndices = LinkedHashMap<Int, String>()
            for (data in module.datas) {
                //println("DATA: $data")
                val ast = data.toAst(module)
                if (ast is Wast.RETURN && ast.expr is Wast.Const) {
                    val memBase = (ast.expr.value as Number).toInt()
                    dataIndices[data.index] = "$memBase"
                    maxMem = Math.max(maxMem, memBase + data.data.size)
                } else {
                    line("private int computeDataIndex${data.index}()") {
                        line(ast.dump(DumpContext(moduleCtx, null)).indenter)
                    }
                    dataIndices[data.index] = "computeDataIndex${data.index}()"
                }
            }

            writeMemoryTools()
            writeOps()
            writeBaseOps()
            syscalls()
            missingSyscalls()

            val maxmMemAlign = maxMem.nextAlignedTo(1024)

            line("static private final java.nio.charset.Charset UTF_8 = java.nio.charset.Charset.forName(\"UTF-8\");")

            line("private int $STACKTOP = $maxmMemAlign + 1024;")
            line("private int $STACK_MAX = $STACKTOP + STACK_INITIAL_SIZE;")
            line("private int STACKTOP_REV = heap.limit();")

            line("private final int $DYNAMICTOP_PTR = 8;")
            line("private final int $tempDoublePtr = 16;")

            line("private void initDynamictop()") {
                line("sw($DYNAMICTOP_PTR, $STACK_MAX);")
                line("sw($tempDoublePtr, $maxmMemAlign);")
            }

            line("public int stackAllocRev(int count) { STACKTOP_REV -= (count + 15) & ~0xF; return STACKTOP_REV; }")
            line("public int allocBytes(byte[] bytes) { int address = stackAllocRev(bytes.length); putBytes(address, bytes); return address; }")
            line("public int allocInts(int[] ints) { int address = stackAllocRev(ints.length * 4); putInts(address, ints); return address; }")
            line("public int allocStringz(String str) { return allocBytes((str + \"\\u0000\").getBytes(UTF_8)); }")

            writeImportedFuncs()

            for (nn in 0 until module.datas.size step initBlockSize) {
                line("private void init$nn()") {
                    for (mm in 0 until initBlockSize) {
                        val n = nn + mm
                        val data = module.datas.getOrNull(n) ?: break
                        val base64 = Base64.getEncoder().encodeToString(data.data)
                        val chunks = base64.splitInChunks(32 * 1024).map { "\"$it\"" }
                        line("putBytesB64(${dataIndices[data.index]}, ${chunks.joinToString(", ")});")
                    }
                }
            }
            for (global in module.globals) {
                if (global.import == null) {
                    val computeName = "compute${moduleCtx.getName(global)}"
                    val getterType = WasmType.Function(listOf(), listOf(global.globalType.type))
                    val ast: Wast.Stm = when {
                        global.e != null -> global.e.toAst(module, WasmFunc(-1, getterType))
                        global.ast != null -> global.ast
                        else -> TODO("Both ${global::e.name} and ${global::ast.name} are null")
                    }
                    val ctx = DumpContext(moduleCtx, null)
                    val value = if (ast is Wast.RETURN) {
                        ast.expr.dump(ctx)
                    } else {
                        line("private ${global.globalType.type.type()} $computeName()") {
                            line(ast.dump(ctx).indenter)
                        }
                        "$computeName()"
                    }
                    line("${global.globalType.type.type()} ${moduleCtx.getName(global)} = $value;")
                }
            }

            val funcToIndices = module.elements.flatMap { it.funcRefs }.withIndex()
                .map { module.getFunction(it.value) to it.index }
                .toMapList()

            for ((type, functions) in module.functions.groupBy { it.type.withoutArgNames() }) {
                val ctx = DumpContext(moduleCtx, WasmFunc(-1, WasmType.Function(listOf(), listOf())))
                val args = type.args.map { "${it.type.type()} ${ctx.getName(it)}" }
                val argsCall = type.args.withIndex().map { ctx.getName(it.value) }.joinToString(", ")
                val rfuncs = functions.map { it to funcToIndices[it] }.filter { it.second != null }
                val argsWithIndex = (listOf("int index") + args).joinToString(", ")
                if (rfuncs.isNotEmpty()) {
                    //run {
                    line("private ${type.retType.type()} invoke_${type.signature}($argsWithIndex)") {
                        line("switch (index)") {
                            for ((func, funcIndices) in rfuncs) {
                                for (funcIdx in (funcIndices ?: continue)) {
                                    if (func.type.retTypeVoid) {
                                        line("case $funcIdx: this.${func.name}($argsCall); return;")
                                    } else {
                                        line("case $funcIdx: return this.${func.name}($argsCall);")
                                    }
                                }
                            }
                        }
                        line("throw new RuntimeException(\"Invalid function (${type.signature}) at index \" + index);")
                    }
                }
            }

            for (func in module.functions) {
                line("// func (${func.index}) : ${func.name}")
                line(dump(func))
            }
        }
    }

    class Breaks() {
        val breaks = LinkedHashSet<AstLabel>()

        operator fun contains(label: AstLabel?) = label in breaks
        fun addLabel(label: AstLabel) {
            if (label.kind == FlowKind.BREAK) {
                breaks += label
            }
        }

        fun addLabelSure(label: AstLabel) {
            breaks += label
        }

        override fun toString(): String = "Breaks($breaks)"
    }

    class DumpResult(val indenter: Indenter, val breaks: Breaks, val unreachable: Boolean)

    fun Breaks.concatResult(result: DumpResult) {
        this.breaks += result.breaks.breaks
    }

    fun DumpResult.appendBreaks(breaks: Breaks) = this.apply { breaks.concatResult(this) }

    class NameAllocator(val fixer: (String) -> String = { it }) {
        val allocatedNames = LinkedHashSet<String>()

        fun allocate(name: String): String {
            var rname = fixer(name)
            while (rname in allocatedNames) {
                rname += "_" // Todo use numeric prefixes?
            }
            allocatedNames += rname
            return rname
        }
    }


    inner class ModuleDumpContext() {
        private val usedNames = langNameAllocator()
        private val globalNames = LinkedHashMap<AstGlobal, String>()
        private val functionNames = LinkedHashMap<String, String>()

        fun getName(func: WasmFuncRef): String = functionNames.getOrPut(func.name) { usedNames.allocate(func.name) }
        fun getName(func: WasmFuncWithType): String =
            functionNames.getOrPut(func.name) { usedNames.allocate(func.name) }

        //fun getName(func: WasmFunc): String = getName(func.ftype)
        fun getName(global: AstGlobal): String = globalNames.getOrPut(global) { usedNames.allocate(global.name) }

        fun getName(global: Wasm.WasmGlobal): String = getName(global.astGlobal)
    }

    inner class DumpContext(val moduleCtx: ModuleDumpContext, val func: WasmFunc?) {
        val phiTypes = LinkedHashSet<WasmType>()
        val debug get() = false
        //val debug get() = func?.name == "_memset"
        val usedNames = langNameAllocator()
        val localNames = LinkedHashMap<AstLocal, String>()
        val labelNames = LinkedHashMap<AstLabel, String>()

        fun getName(local: AstLocal): String = localNames.getOrPut(local) { usedNames.allocate(local.name) }
        fun getName(label: AstLabel): String = labelNames.getOrPut(label) { usedNames.allocate(label.name) }
        fun getName(global: AstGlobal): String = moduleCtx.getName(global)
    }

    fun dump(ctx: DumpContext, stm: Wast.Stm, out: Indenter = Indenter { }): DumpResult {
        return stm.dump(ctx, out)
    }

    // @TODO: We should clean-up the AST before in other phase instead of directly while generating code.
    fun Wast.Stm.dump(ctx: DumpContext, out: Indenter = Indenter { }): DumpResult {
        val breaks = Breaks()
        var unreachable = false
        when (this) {
            is Wast.Stms -> {
                for (e in stms) {
                    val result = e.dump(ctx, out).appendBreaks(breaks)
                    if (result.unreachable) {
                        unreachable = true
                        break // Stop
                    }
                }
            }
            is Wast.SetLocal -> out.line(writeSetLocal(ctx.getName(local), expr.dump(ctx)))
            is Wast.SetGlobal -> out.line(writeSetGlobal(ctx.getName(global), expr.dump(ctx)))
            is Wast.RETURN -> run { out.line(writeReturn(expr.dump(ctx))); unreachable = true }
            is Wast.RETURN_VOID -> run { out.line(writeReturnVoid()); unreachable = true }
            is Wast.BLOCK -> {
                lateinit var result: DumpResult
                val optLabel = if (label != null) "${ctx.getName(label)}: " else ""
                if (optLabel.isEmpty()) {
                    result = this.stm.dump(ctx, out).appendBreaks(breaks)
                } else {
                    //out.line("${optLabel}do") {
                    out.line(optLabel) {
                        result = this.stm.dump(ctx, out).appendBreaks(breaks)
                    }
                    //out.line("while (false);")
                }
                unreachable = result.unreachable && (label !in breaks)
                if (ctx.debug) println("BLOCK. ${ctx.func?.name} (block_label=${label?.name}). Unreachable: $unreachable, $breaks")
            }
            is Wast.LOOP -> {
                lateinit var result: DumpResult
                val optLabel = if (label != null) "${ctx.getName(label)}: " else ""
                out.line("${optLabel}while (true)") {
                    result = this.stm.dump(ctx, out).appendBreaks(breaks)
                    if (result.unreachable) {
                        out.line("//break;")
                    } else {
                        out.line("break;")
                        if (label != null) breaks.addLabelSure(label)
                    }
                }
                unreachable = label !in breaks
                if (ctx.debug) println("LOOP. ${ctx.func?.name} (loop_label=${label?.name}). Unreachable: $unreachable, $breaks")
            }
            is Wast.IF -> {
                out.line("if (${dumpBoolean(cond, ctx)})") {
                    val result = this.btrue.dump(ctx, out).appendBreaks(breaks)
                }
            }
            is Wast.IF_ELSE -> {
                out.line("if (${dumpBoolean(cond, ctx)})") {
                    val result = this.btrue.dump(ctx, out).appendBreaks(breaks)
                }
                out.line("else") {
                    val result = this.bfalse.dump(ctx, out).appendBreaks(breaks)
                }
            }
            is Wast.BR -> {
                out.line(writeGoto(label, ctx))
                breaks.addLabel(this.label)
                unreachable = true
            }
            is Wast.BR_IF -> {
                out.line("if (${dumpBoolean(cond, ctx)}) " + writeGoto(label, ctx))
                breaks.addLabel(label)
            }
            is Wast.BR_TABLE -> {
                out.line("switch (${this.subject.dump(ctx)})") {
                    for ((index, label) in this.labels) {
                        out.line("case $index: ${label.goto(ctx)};")
                        breaks.addLabel(label)
                    }
                    out.line("default: ${this.default.goto(ctx)};")
                    breaks.addLabel(default)
                }
            }
            is Wast.STM_EXPR -> {
                val exprStr = this.expr.dump(ctx)
                //var exprStr = this.expr.dump(ctx)
                //removeParen@while (exprStr.startsWith("(") && exprStr.endsWith(")")) {
                //    var openCount = 0
                //    for (n in 0 until exprStr.length) {
                //        val c = exprStr[n]
                //        if (c == '(') {
                //            openCount++
                //        } else if (c == ')') {
                //            if (openCount == 0 && n < exprStr.length - 1) {
                //                break@removeParen
                //            }
                //            openCount--
                //        }
                //    }
                //    exprStr = exprStr.substring(1, exprStr.length - 1)
                //}

                if (this.expr is Wast.Const || this.expr is Wast.Local || this.expr is Wast.Global || this.expr is Wast.Unop || this.expr is Wast.Binop || this.expr is Wast.Terop) {
                    out.line(writeExprStatementNoStm(exprStr))
                } else {
                    out.line(writeExprStatement(exprStr))
                }
            }
            is Wast.WriteMemory -> out.line(writeWriteMemory(op, address.dump(ctx), offset, align, value.dump(ctx)))
            is Wast.SetPhi -> out.line(writeSetPhi("phi_${this.blockType}", this.value.dump(ctx)))
            is Wast.Unreachable -> {
                out.line(writeUnreachable())
                unreachable = true
            }
            is Wast.NOP -> out.line(writeNop())
            else -> out.line("??? $this")
        }
        return DumpResult(out, breaks, unreachable)
    }

    protected open fun dumpBoolean(expr: Wast.Expr, ctx: DumpContext): String {
        when (expr) {
            is Wast.Unop -> when (expr.op) {
                WasmOp.Op_i32_eqz, WasmOp.Op_i64_eqz -> return "${expr.expr.dump(ctx)} == 0"
                else -> Unit
            }
            is Wast.Binop -> {
                val l = expr.l.dump(ctx)
                val r = expr.r.dump(ctx)
                when (expr.op) {
                    WasmOp.Op_i32_eq, WasmOp.Op_i64_eq, WasmOp.Op_f32_eq, WasmOp.Op_f64_eq -> return "$l == $r"
                    WasmOp.Op_i32_ne, WasmOp.Op_i64_ne, WasmOp.Op_f32_ne, WasmOp.Op_f64_ne -> return "$l != $r"
                    WasmOp.Op_i32_gt_s, WasmOp.Op_i64_gt_s, WasmOp.Op_f32_gt, WasmOp.Op_f64_gt -> return "$l > $r"
                    WasmOp.Op_i32_lt_s, WasmOp.Op_i64_lt_s, WasmOp.Op_f32_lt, WasmOp.Op_f64_lt -> return "$l < $r"
                    WasmOp.Op_i32_ge_s, WasmOp.Op_i64_ge_s, WasmOp.Op_f32_ge, WasmOp.Op_f64_ge -> return "$l >= $r"
                    WasmOp.Op_i32_le_s, WasmOp.Op_i64_le_s, WasmOp.Op_f32_le, WasmOp.Op_f64_le -> return "$l <= $r"
                    else -> Unit
                }
            }
        }
        return expr.dump(ctx) + " != 0"
    }

    protected open fun writeGoto(label: AstLabel, ctx: DumpContext) = Indenter(label.goto(ctx) + ";")
    protected open fun writeSetLocal(localName: String, expr: String) = Indenter("$localName = $expr;")
    protected open fun writeSetGlobal(globalName: String, expr: String) = Indenter("this.$globalName = $expr;")
    protected open fun writeReturn(expr: String) = Indenter("return $expr;")
    protected open fun writeReturnVoid() = Indenter("return;")
    protected open fun writeExprStatement(expr: String) = Indenter("$expr;")
    protected open fun writeExprStatementNoStm(expr: String) = Indenter("// $expr; // Not a statement")
    protected open fun writeUnreachable() = Indenter("// unreachable")
    protected open fun writeNop() = Indenter("// nop")
    protected open fun writeSetPhi(phiName: String, expr: String) = writeSetLocal(phiName, expr)

    protected open fun const(value: Int) = "$value"
    protected open fun const(value: Long) = "${value}L"
    protected open fun const(value: Float) = "${value}f"
    protected open fun const(value: Double) = "$value"
    protected open fun unop(op: WasmOp, vd: String) = when (op) {
        WasmOp.Op_f32_neg, WasmOp.Op_f64_neg -> "-($vd)"
        WasmOp.Op_f64_promote_f32 -> vd.cdouble
        WasmOp.Op_f32_demote_f64 -> vd.cfloat
        else -> "$op($vd)"
    }

    protected open fun binop(op: WasmOp, ld: String, rd: String) = when (op) {
        WasmOp.Op_i32_add, WasmOp.Op_i64_add, WasmOp.Op_f32_add, WasmOp.Op_f64_add -> "($ld + $rd)"
        WasmOp.Op_i32_sub, WasmOp.Op_i64_sub, WasmOp.Op_f32_sub, WasmOp.Op_f64_sub -> "($ld - $rd)"
        WasmOp.Op_i32_mul, WasmOp.Op_i64_mul, WasmOp.Op_f32_mul, WasmOp.Op_f64_mul -> "($ld * $rd)"
        WasmOp.Op_i32_div_s, WasmOp.Op_i64_div_s, WasmOp.Op_f32_div, WasmOp.Op_f64_div -> "($ld / $rd)"
        WasmOp.Op_i32_rem_s, WasmOp.Op_i64_rem_s -> "($ld % $rd)"
        WasmOp.Op_i32_and, WasmOp.Op_i64_and -> "($ld $AND $rd)"
        WasmOp.Op_i32_or, WasmOp.Op_i64_or -> "($ld $OR $rd)"
        WasmOp.Op_i32_xor, WasmOp.Op_i64_xor -> "($ld $XOR $rd)"
        WasmOp.Op_i32_shl, WasmOp.Op_i64_shl -> "($ld $SHL $rd)"
        WasmOp.Op_i32_shr_s, WasmOp.Op_i64_shr_s -> "($ld $SHR $rd)"
        WasmOp.Op_i32_shr_u, WasmOp.Op_i64_shr_u -> "($ld $SHRU $rd)"
        else -> "$op($ld, $rd)"
    }

    protected open fun terop(op: WasmOp, cond: String, strue: String, sfalse: String) =
        "((($cond)) ? ($strue) : ($sfalse))"

    protected open fun getGlobal(name: String) = "this.$name"
    protected open fun getLocal(name: String) = name
    protected open fun getPhi(name: String) = getLocal(name)
    protected open fun readMemory(op: WasmOp, address: String, offset: Int, align: Int): String {
        val raddr = if (offset != 0) "$address + $offset" else address
        return when (op) {
            WasmOp.Op_i32_load8_s -> "this.getByte($raddr)"
            WasmOp.Op_i32_load -> "this.getInt($raddr)"
            WasmOp.Op_i64_load -> "this.getLong($raddr)"
            WasmOp.Op_f32_load -> "this.getFloat($raddr)"
            WasmOp.Op_f64_load -> "this.getDouble($raddr)"
            else -> "$op($address, $offset, $align)"
        }
    }

    protected open fun writeWriteMemory(op: WasmOp, address: String, offset: Int, align: Int, expr: String): Indenter {
        val raddr = if (offset != 0) "$address + $offset" else address
        return when (op) {
            WasmOp.Op_i32_store -> Indenter("this.putInt($raddr, $expr);")
            WasmOp.Op_i32_store8 -> Indenter("this.putByte($raddr, $expr);")
            WasmOp.Op_i32_store16 -> Indenter("this.putShort($raddr, $expr);")
            WasmOp.Op_i64_store -> Indenter("this.putLong($raddr, $expr);")
            WasmOp.Op_f32_store -> Indenter("this.putFloat($raddr, $expr);")
            WasmOp.Op_f64_store -> Indenter("this.putDouble($raddr, $expr);")
            else -> Indenter("$op($address, $offset, $align, $expr);")
        }
    }

    fun Wast.Expr.dump(ctx: DumpContext): String {
        return when (this) {
            is Wast.Const -> when (this.type) {
                WasmType.i32 -> const(value as Int)
                WasmType.i64 -> const(value as Long)
                WasmType.f32 -> const(value as Float)
                WasmType.f64 -> const(value as Double)
                else -> "(${this.value})"
            }
            is Wast.TeeLocal -> "(${ctx.getName(local)} = ${this.expr.dump(ctx)})"
            is Wast.Local -> getLocal(ctx.getName(local))
            is Wast.Global -> getGlobal(ctx.getName(global))
            is Wast.Unop -> unop(op, expr.dump(ctx))
            is Wast.Terop -> terop(op, dumpBoolean(cond, ctx), etrue.dump(ctx), efalse.dump(ctx))
            is Wast.Binop -> binop(op, l.dump(ctx), r.dump(ctx))
            is Wast.CALL -> {
                "this.${ctx.moduleCtx.getName(func)}(${this.args.joinToString(", ") { it.dump(ctx) }})"
            }
        //is A.CALL_INDIRECT -> "((Op_getFunction(${this.address.dump()}) as (${this.type.type()}))" + "(" + this.args.map { it.dump() }.joinToString(
            is Wast.CALL_INDIRECT -> {
                "invoke_${type.signature}(${address.dump(ctx)}, " + args.joinToString(", ") { it.dump(ctx) } + ")"
            }
            is Wast.ReadMemory -> readMemory(op, address.dump(ctx), offset, align)
            is Wast.Phi -> run { ctx.phiTypes += this.type; getPhi("phi_${this.type}") }
            is Wast.BLOCK_EXPR -> "TODO_${this.type}(\"BLOCK_EXPR not implemented\")"
            else -> "???($this)"
        }
    }

    open fun Indenter.writeMemoryTools() {
        FUNC("putBytes", void, int("address"), BA("data"), int("offset"), int("size")) { "System.arraycopy(data, offset, heapBytes, address, size);" }
        FUNC("putBytes", void, int("address"), BA("data")) { "putBytes(address, data, 0, ${"data".asize});" }
        FUNC("putInts", void, int("address"), IA("data")) { Indenter.genString { FOR("n", "0", "data".asize) { line("heap.putInt(address + n * 4, data[n]);") } } }
        line("public void putBytesB64(int address, String... datas) { final StringBuilder out = new StringBuilder(); for (int n = 0; n < datas.length; n++) out.append(datas[n]); putBytes(address, java.util.Base64.getDecoder().decode(out.toString())); }")
        line("public void putString(int address, String string, java.nio.charset.Charset charset) { putBytes(address, string.getBytes(charset)); }")
        line("public byte[] getBytes(int address, int size) { return java.util.Arrays.copyOfRange(heapBytes, address, address + size); }")
        line("public byte[] getBytez(int address) { return getBytez(address, java.lang.Integer.MAX_VALUE); }")
        line("public int strlen(final int address) { int n = 0; while (heapBytes[address + n] != 0) n++; return n; }")
        line("public byte[] getBytez(int address, int max) { return getBytes(address, strlen(address)); }")
        line("public String getStringz(int address, java.nio.charset.Charset charset) { return new java.lang.String(getBytez(address), charset); }")
        line("public String getStringz(int address) { return getStringz(address, UTF_8); }")
    }

    fun dump(func: WasmFunc): Indenter = Indenter {
        val bodyAst = func.getAst(module)
        if (bodyAst != null) {
            val visibility = if (func.export != null) "public " else "private "
            val ctx = DumpContext(moduleCtx, func)
            val args = func.type.args.joinToString(", ") { "${it.type.type()} " + ctx.getName(it) + "" }
            val arg = func.type.args
            line("$visibility${func.type.retType.type()} ${moduleCtx.getName(func)}($args)") {
                when (func.name) {
                    "\$_memmove", "\$_memcpy" -> {
                        val dst = ctx.getName(arg[0])
                        val src = ctx.getName(arg[1])
                        val count = ctx.getName(arg[2])
                        line("System.arraycopy(heapBytes, $src, heapBytes, $dst, $count);")
                        line("return $dst;")
                    }
                    "\$_memset" -> {
                        val ptr = ctx.getName(arg[0])
                        val value = ctx.getName(arg[1])
                        val num = ctx.getName(arg[2])
                        line("java.util.Arrays.fill(heapBytes, $ptr, $ptr + $num, ${value.cbyte});")
                        line("return $ptr;")
                    }
                    else -> {
                        val argsSet = func.type.args.toSet()
                        for (local in bodyAst.getLocals()) {
                            if (local in argsSet) continue
                            line("${local.type.type()} ${ctx.getName(local)} = ${local.type.default()};")
                        }
                        val res = bodyAst.dump(ctx)
                        for (phi in ctx.phiTypes) line("${phi.type()} phi_$phi = ${phi.default()};")
                        line(res.indenter)
                    }
                }
            }
        }
    }

    fun Indenter.syscalls() {
        line("private int lastFD = 10;")
        line("private java.util.Map<Integer, java.io.RandomAccessFile> files = new java.util.HashMap<Integer, java.io.RandomAccessFile>();")

        syscall(WasmSyscall.SYS_open) {
            line("final String pathName = getStringz(lw(address + 0));")
            line("final int flags = lw(address + 4);")
            line("final int mode = lw(address + 8);")
            line("final int fd = lastFD++;")
            line("final String smode = ((flags & ($O_WRONLY|$O_RDWR)) != 0) ? \"rw\" : \"r\";")
            line("final java.io.File file = new java.io.File(pathName);")
            line("final java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, smode);")
            line("files.put(fd, raf);")
            line("return fd;")
        }

        syscall(WasmSyscall.SYS_close) {
            line("final int fd = lw(address + 0);")
            //line("System.out.println(\"close fd=\" + fd);")
            line("java.io.RandomAccessFile raf = files.remove(fd);")
            //line("files.remove(fd);")
            line("if (raf != null) raf.close();")
            line("return 0;")
        }

        syscall(WasmSyscall.SYS_ioctl) {
            line("final int fd = lw(address + 0);")
            line("final int param = lw(address + 4);")
            //line("System.out.println(\"ioctl fd=\" + fd + \", param=\" + param);")
            line("return 0;")
        }

        syscall(WasmSyscall.SYS_fcntl64) {
            line("final int fd = lw(address + 0);")
            line("final int paramH = lw(address + 4);")
            line("final int paramL = lw(address + 8);")
            //line("System.out.println(\"ioctl fd=\" + fd + \", param=\" + param);")
            line("return 0;")
        }

        syscall(WasmSyscall.SYS__llseek) {
            line("final int fd = lw(address + 0);")
            line("final int offsetH = lw(address + 4);")
            line("final int offsetL = lw(address + 8);")
            line("final int result = lw(address + 12);")
            line("final int whence = lw(address + 16);")
            line("final long offset = (long)offsetL; // offsetH unused in emscripten")
            //line("System.out.printf(\"llseek fd=%d, offsetH=%d, offsetL=%d, result=%d, whence=%d\", fd, offsetH, offsetL, result, whence);")
            line("final java.io.RandomAccessFile raf = files.get(fd);")
            line("if (raf == null) return -1;")
            line("switch (whence)") {
                line("case 0: raf.seek(offset); break;") // SEEK_SET
                line("case 1: raf.seek(raf.getFilePointer() + offset); break;") // SEEK_CUR
                line("case 2: raf.seek(raf.length() + offset); break;") // SEEK_END
            }
            line("if (result != 0) sdw(result, raf.getFilePointer());")
            line("return 0;")
        }

        syscall(WasmSyscall.SYS_readv) {
            line("final int fd = lw(address + 0);")
            line("final int iov = lw(address + 4);")
            line("final int iovcnt = lw(address + 8);")
            //line("System.out.println(\"readv fd=\" + fd + \", iov=\" + iov + \", iovcnt=\" + iovcnt);")
            line("final java.io.RandomAccessFile raf = files.get(fd);")
            line("int ret = 0;")
            line("end:")
            FOR("cc", from = "0", until = "iovcnt") {
                line("int ptr = lw((iov + (cc * 8)) + 0);")
                line("int len = lw((iov + (cc * 8)) + 4);")
                //line("System.out.println(\"chunk: ptr=\" + ptr + \",len=\" + len);")

                line("int res = 0;")
                line("if (raf != null)") {
                    line("res = raf.read(heapBytes, ptr, len);")
                }
                line("else if (fd == 0)") {
                    line("res = System.in.read(heapBytes, ptr, len);")
                }
                line("if (res <= 0) break end;")
                line("ret += res;")
            }
            line("return ret;")
        }

        syscall(WasmSyscall.SYS_writev) {
            val RandomAccessFileType = type("java.io.RandomAccessFile")
            LOCAL(int("fd"), "lw(address + 0)")
            LOCAL(int("iov"), "lw(address + 4)")
            LOCAL(int("iovcnt"), "lw(address + 8)")
            //line("System.out.println(\"writev fd=\" + fd + \", iov=\" + iov + \", iovcnt=\" + iovcnt);")
            LOCAL(RandomAccessFileType("raf"), "files.get(fd)")
            LOCAL(int("ret"), "0")
            FOR("cc", from = "0", until = "iovcnt") {
                LOCAL(int("ptr"), "lw((iov + (cc * 8)) + 0)")
                LOCAL(int("len"), "lw((iov + (cc * 8)) + 4)")
                //line("System.out.println(\"chunk: ptr=\" + ptr + \",len=\" + len);")
                line("if (len == 0) continue;")

                line("if (raf != null)") {
                    line("raf.write(heapBytes, ptr, len);")
                }
                line("else if (fd == 1)") {
                    line("System.out.write(heapBytes, ptr, len);")
                }
                line("else if (fd == 2)") {
                    line("System.err.write(heapBytes, ptr, len);")
                }

                line("ret += len;")
            }
            line("return ret;")
        }
    }

    // https://webassembly.github.io/spec/core/exec/numerics.html
    fun Indenter.writeOps(): Unit {
        FUNC("b2i", int, bool("v")) { ternary("v", "1", "0") }

        line("private void TODO() { throw new RuntimeException(); }")
        line("private int TODO_i32() { throw new RuntimeException(); }")
        line("private long TODO_i64() { throw new RuntimeException(); }")
        line("private float TODO_f32() { throw new RuntimeException(); }")
        line("private double TODO_f64() { throw new RuntimeException(); }")

        line("private void TODO(String reason) { throw new RuntimeException(reason); }")
        line("private int TODO_i32(String reason) { throw new RuntimeException(reason); }")
        line("private long TODO_i64(String reason) { throw new RuntimeException(reason); }")
        line("private float TODO_f32(String reason) { throw new RuntimeException(reason); }")
        line("private double TODO_f64(String reason) { throw new RuntimeException(reason); }")

        line("private void sb(int address, int value) { Op_i32_store8(address, 0, 0, value); }")
        line("private void sh(int address, int value) { Op_i32_store16(address, 0, 1, value); }")
        line("private void sw(int address, int value) { Op_i32_store(address, 0, 2, value); }")
        line("private void sdw(int address, long value) { Op_i64_store(address, 0, 3, value); }")

        line("private int lb(int address) { return Op_i32_load8_s(address, 0, 0); }")
        line("private int lbu(int address) { return Op_i32_load8_u(address, 0, 0); }")
        line("private int lh(int address) { return Op_i32_load16_s(address, 0, 1); }")
        line("private int lhu(int address) { return Op_i32_load16_u(address, 0, 1); }")
        line("private int lw(int address) { return Op_i32_load(address, 0, 2); }")
        line("private long ldw(int address) { return Op_i64_load(address, 0, 3); }")

        line("private int getByte(int a) { return (int)heap.get(a); }")
        line("private int getShort(int a) { return (int)heap.getShort(a); }")
        line("private int getInt(int a) { return heap.getInt(a); }")
        line("private long getLong(int a) { return heap.getLong(a); }")
        line("private float getFloat(int a) { return heap.getFloat(a); }")
        line("private double getDouble(int a) { return heap.getDouble(a); }")

        line("private void putByte(int a, int v) { heap.put(a, (byte)v); }")
        line("private void putShort(int a, int v) { heap.putShort(a, (short)v); }")
        line("private void putInt(int a, int v) { heap.putInt(a, v); }")
        line("private void putLong(int a, long v) { heap.putLong(a, v); }")
        line("private void putFloat(int a, float v) { heap.putFloat(a, v); }")
        line("private void putDouble(int a, double v) { heap.putDouble(a, v); }")

        //Op_memory_size
        //Op_memory_grow
        //Op_i32_const
        //Op_i64_const
        //Op_f32_const
        //Op_f64_const

        line("private int checkAddress(int address, int offset, int align, int size)") {
            line("int raddress = address + offset;")
            line("if (raddress < 0 || raddress >= heap.limit() - 4)") {
                line("System.out.printf(\"ADDRESS: %d (%d + %d) align=%d, size=%d\\n\", raddress, address, offset, align, size);")
            }
            //line("if ((raddress & ((1 << align) - 1)) != 0)") {
            //    line("System.out.printf(\"UNALIGNED ACCESS %d (%d + %d) align=%d, size=%d!\\n\", raddress, address, offset, align, size);")
            //}
            line("return raddress;")
        }
        line("")

        line("private int checkARead(int address, int offset, int align, int size)") {
            line("return checkAddress(address, offset, align, size);")
        }
        line("")

        line("private int checkAWrite(int address, int offset, int align, int size)") {
            line("return checkAddress(address, offset, align, size);")
        }
        line("")
    }

    open fun Indenter.LOCAL(arg: MyArg, expr: String) {
        line("${arg.type.type()} ${arg.name} = $expr;")
    }

    open fun Indenter.FOR(v: String, from: String, until: String, body: Indenter.() -> Unit) {
        line("for (int $v = $from; $v < $until; $v++)") {
            body()
        }
    }

    open fun Indenter.FUNC(name: String, ret: WasmType, vararg args: MyArg, pub: Boolean = true, body: () -> String) {
        val rbody = if (ret == void) body() else "return ${body()};"
        val s = if (pub) "public" else "private"
        line("$s ${ret.type()} $name(${args.argstr()}) { $rbody }")
    }

    open fun WasmType.default(): String = when (this) {
        WasmType.i64 -> "0L"
        WasmType.f32 -> "0f"
        WasmType.f64 -> "0.0"
        else -> "0"
    }

    open fun WasmType.type(): String = when (this) {
        WasmType.void -> "void"
        WasmType._i8 -> "byte"
        WasmType._i16 -> "short"
        WasmType.i32 -> "int"
        WasmType.i64 -> "long"
        WasmType.f32 -> "float"
        WasmType.f64 -> "double"
        is WasmType._ARRAY -> "${this.element.type()}[]"
        is WasmType.Function -> "(${this.args.joinToString(", ") { it.type.type() }}) -> ${this.retType.type()}"
        else -> "$this"
    }

    fun AstLabel.goto(ctx: DumpContext) = "${this.kind.keyword} ${ctx.getName(this)}"
}
