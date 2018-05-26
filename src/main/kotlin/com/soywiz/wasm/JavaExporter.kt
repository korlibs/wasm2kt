package com.soywiz.wasm

import com.soywiz.korio.error.*
import com.soywiz.korio.util.*
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet
import kotlin.math.*

class JavaExporter(val module: WasmModule) : BaseJavaExporter() {
    val moduleCtx = ModuleDumpContext()

    val functionsWithImport = module.functions.filter { it.import != null }
    val functionsByImport = functionsWithImport.map { it.import!!.importPair to it }.toMap()
    val handledFunctionsWithImport = LinkedHashSet<WasmFunc>()

    val globalsWithImport = module.globals.filter { it.import != null }
    val globalsByImport = globalsWithImport.map { it.import!!.importPair to it }.toMap()
    val handledGlobalsWithImport = LinkedHashSet<Wasm.WasmGlobal>()

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

    override fun dump(): Indenter = Indenter {
        line("public class Module") {
            val mainFunc = module.functions.firstOrNull { it.export?.name == "_main" }
            if (mainFunc != null) {
                val funcName = moduleCtx.getName(mainFunc)
                line("static public void main(String[] args)") {
                    when (mainFunc.type.args.size) {
                        0 -> line("new Module().$funcName();")
                        1 -> line("new Module().$funcName(0);")
                        2 -> line("new Module().$funcName(0, 0);")
                    }

                }
            }

            line("static public final int heapSize = 16 * 1024 * 1024; // 16 MB")
            line("static public final int stackSize = 32 * 1024; // 32 KB ")

            line("public java.nio.ByteBuffer heap = java.nio.ByteBuffer.allocate(heapSize).order(java.nio.ByteOrder.nativeOrder());")

            line("private int ABORT = -1;")
            line("private int DYNAMICTOP_PTR = 0;")
            line("private int STACK_INITIAL_SIZE = stackSize;")
            line("private int STACK_MAX = heap.limit();")
            line("private int STACKTOP = STACK_MAX - STACK_INITIAL_SIZE;")
            line("private int allocStack(int count) {  STACKTOP -= count; return STACKTOP; }")
            line("private int tempDoublePtr = allocStack(1024);")

            line("private double NaN = Double.NaN;")
            line("private double Infinity = Double.POSITIVE_INFINITY;")

            // https://webassembly.github.io/spec/core/exec/numerics.html
            line("int Op_i32_add(int l, int r) { return l + r; }")
            line("int Op_i32_sub(int l, int r) { return l - r; }")
            line("int Op_i32_mul(int l, int r) { return l * r; }")
            line("int Op_i32_div_u(int l, int r) { return java.lang.Integer.divideUnsigned(l, r); }")
            line("int Op_i32_div_s(int l, int r) { return l / r; }")
            line("int Op_i32_rem_u(int l, int r) { return java.lang.Integer.remainderUnsigned(l, r); }")
            line("int Op_i32_rem_s(int l, int r) { return l % r; }")
            line("int Op_i32_and(int l, int r) { return l & r; }")
            line("int Op_i32_xor(int l, int r) { return l ^ r; }")
            line("int Op_i32_or(int l, int r) { return l | r; }")
            line("int Op_i32_shr_u(int l, int r) { return l >>> r; }")
            line("int Op_i32_shr_s(int l, int r) { return l >> r; }")
            line("int Op_i32_shl(int l, int r) { return l << r; }")

            line("long Op_i64_add(long l, long r) { return l + r; }")
            line("long Op_i64_sub(long l, long r) { return l - r; }")
            line("long Op_i64_mul(long l, long r) { return l * r; }")
            line("long Op_i64_div_u(long l, long r) { return java.lang.Long.divideUnsigned(l, r); }")
            line("long Op_i64_div_s(long l, long r) { return l / r; }")
            line("long Op_i64_rem_u(long l, long r) { return java.lang.Long.remainderUnsigned(l, r); }")
            line("long Op_i64_rem_s(long l, long r) { return l % r; }")
            line("long Op_i64_and(long l, long r) { return l & r; }")
            line("long Op_i64_xor(long l, long r) { return l ^ r; }")
            line("long Op_i64_or(long l, long r) { return l | r; }")
            line("long Op_i64_shr_u(long l, long r) { return l >>> (int)r; }")
            line("long Op_i64_shr_s(long l, long r) { return l >> (int)r; }")
            line("long Op_i64_shl(long l, long r) { return l << (int)r; }")

            line("double Op_f64_neg(double v) { return (-v); }")
            line("double Op_f64_add(double l, double r) { return (l + r); }")
            line("double Op_f64_sub(double l, double r) { return (l - r); }")
            line("double Op_f64_mul(double l, double r) { return (l * r); }")
            line("double Op_f64_div(double l, double r) { return (l / r); }")
            line("double Op_f64_rem(double l, double r) { return (l % r); }")

            line("long Op_i64_reinterpret_f64(double v) { return java.lang.Double.doubleToRawLongBits(v); }")
            line("double Op_f64_reinterpret_i64(long v) { return (java.lang.Double.longBitsToDouble(v)); }")
            line("int Op_i32_reinterpret_f32(float v) { return java.lang.Float.floatToRawIntBits(v); }")
            line("float Op_f32_reinterpret_i32(int v) { return java.lang.Float.intBitsToFloat(v); }")

            line("double Op_f64_convert_u_i32(int v) { return (double)((long)v & 0xFFFFFFFFL); }")
            line("double Op_f64_convert_s_i32(int v) { return (double)v; }")

            line("int Op_i32_trunc_u_f64(double v) {")
            line("    if (v <= 0.0) return 0;")
            line("    if (v >= 4294967296.0) return (int)4294967296L;")
            line("    return (int)v;")
            line("}")
            line("int Op_i32_trunc_s_f64(double v) {")
            line("    if (v <= (double)java.lang.Integer.MIN_VALUE) return java.lang.Integer.MIN_VALUE;")
            line("    if (v >= (double)java.lang.Integer.MAX_VALUE) return java.lang.Integer.MAX_VALUE;")
            line("    return (int)v;")
            line("}")

            line("int b2i(boolean v)                { return v ? 1 : 0; }")
            line("int Op_i32_wrap_i64(long v)       { return (int)(v & 0xFFFFFFFFL); }")
            line("long Op_i64_extend_s_i32(int v)   { return (long)v; }")
            line("long Op_i64_extend_u_i32(int v)   { return (long)v & 0xFFFFFFFFL; }")
            line("int Op_i32_eqz(int l)             { return b2i(l == 0); }")
            line("int Op_i32_eq(int l, int r      ) { return b2i(l == r); }")
            line("int Op_i32_ne(int l, int r      ) { return b2i(l != r); }")
            line("int Op_i32_lt_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) < 0); }")
            line("int Op_i32_gt_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) > 0); }")
            line("int Op_i32_le_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) <= 0); }")
            line("int Op_i32_ge_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) >= 0); }")
            line("int Op_i32_lt_s(int l, int r    ) { return b2i(l < r); }")
            line("int Op_i32_le_s(int l, int r    ) { return b2i(l <= r); }")
            line("int Op_i32_gt_s(int l, int r    ) { return b2i(l > r); }")
            line("int Op_i32_ge_s(int l, int r    ) { return b2i(l >= r); }")

            line("int Op_i64_eqz (long l          ) { return b2i(l == 0L); }")
            line("int Op_i64_eq  (long l, long r  ) { return b2i(l == r); }")
            line("int Op_i64_ne  (long l, long r  ) { return b2i(l != r); }")
            line("int Op_i64_lt_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) < 0); }")
            line("int Op_i64_gt_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) > 0); }")
            line("int Op_i64_le_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) <= 0); }")
            line("int Op_i64_ge_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) >= 0); }")
            line("int Op_i64_lt_s(long l, long r  ) { return b2i(l < r); }")
            line("int Op_i64_le_s(long l, long r  ) { return b2i(l <= r); }")
            line("int Op_i64_gt_s(long l, long r  ) { return b2i(l > r); }")
            line("int Op_i64_ge_s(long l, long r  ) { return b2i(l >= r); }")
            line("int Op_f64_eq(double l, double r) { return b2i(l == r); }")
            line("int Op_f64_ne(double l, double r) { return b2i(l != r); }")
            line("int Op_f64_lt(double l, double r) { return b2i(l < r); }")
            line("int Op_f64_le(double l, double r) { return b2i(l <= r); }")
            line("int Op_f64_gt(double l, double r) { return b2i(l > r); }")
            line("int Op_f64_ge(double l, double r) { return b2i(l >= r); }")

            line("int checkAddress(int address, int offset) {")
            line("    int raddress = address + offset;")
            line("    if (raddress < 0 || raddress >= heap.limit() - 4) {")
            line("        System.out.printf(\"ADDRESS: %d (%d + %d)\\n\", raddress, address, offset);")
            line("    }")
            line("    return raddress;")
            line("}")
            line("")
            line("float Op_f32_load(int address, int offset, int align) { return heap.getFloat(checkAddress(address, offset)); }")
            line("double Op_f64_load(int address, int offset, int align) { return heap.getDouble(checkAddress(address, offset)); }")
            line("long Op_i64_load(int address, int offset, int align) { return heap.getLong(checkAddress(address, offset)); }")
            line("int Op_i32_load(int address, int offset, int align) { return heap.getInt(checkAddress(address, offset)); }")
            line("int Op_i32_load8_s(int address, int offset, int align) {")
            line("    int raddr = checkAddress(address, offset);")
            line("    int value = (int)heap.get(raddr);")
            line("    return value;")
            line("}")

            line("int Op_i32_load8_u(int address, int offset, int align) { return Op_i32_load8_s(address, offset, align) & 0xFF; }")

            line("void Op_f32_store(int address, int offset, int align, float value) { heap.putFloat(checkAddress(address, offset), value); }")
            line("void Op_f64_store(int address, int offset, int align, double value) { heap.putDouble(checkAddress(address, offset), value); }")
            line("void Op_i64_store(int address, int offset, int align, long value) { heap.putLong(checkAddress(address, offset), value); }")
            line("void Op_i32_store(int address, int offset, int align, int value) { heap.putInt(checkAddress(address, offset), value); }")
            line("void Op_i32_store8(int address, int offset, int align, int value) { heap.put(checkAddress(address, offset), (byte)value); }")
            line("void Op_i32_store16(int address, int offset, int align, int value) { heap.putShort(checkAddress(address, offset), (short)value); }")
            line("void Op_i64_store8(int address, int offset, int align, long value) { Op_i32_store8(address, offset, align, (int)value); }")
            line("void Op_i64_store16(int address, int offset, int align, long value) { Op_i32_store16(address, offset, align, (int)value); }")
            line("void Op_i64_store32(int address, int offset, int align, long value) { Op_i32_store(address, offset, align, (int)value); }")
            line("void sb(int address, int value) { Op_i32_store8(address, 0, 0, value); }")
            line("void sh(int address, int value) { Op_i32_store16(address, 0, 1, value); }")
            line("void sw(int address, int value) { Op_i32_store(address, 0, 2, value); }")
            line("int lw(int address) { return Op_i32_load(address, 0, 2); }")
            line("int lb(int address) { return Op_i32_load8_s(address, 0, 0); }")
            line("int lbu(int address) { return Op_i32_load8_u(address, 0, 0); }")
            //line("int ___syscall(int syscall, int address) { throw new RuntimeException(\"syscall \" + syscall); }")

            line("int ___syscall(int syscall, int address) {")
            line("    switch (syscall) {")
            line("        case 54: {")
            line("            int fd = lw(address + 0);")
            line("            int param = lw(address + 4);")
            line("            return 0;")
            line("        }")
            line("        case 146: {")
            line("            int stream = lw(address + 0);")
            line("            int iov = lw(address + 4);")
            line("            int iovcnt = lw(address + 8);")
            line("            int ret = 0;")
            line("            for (int cc = 0; cc < iovcnt; cc++) {")
            line("                int ptr = lw((iov + (cc * 8)) + 0);")
            line("                int len = lw((iov + (cc * 8)) + 4);")
            line("                for (int n = 0; n < len; n++) {")
            line("                    printChar(stream, lbu(ptr + n));")
            line("                }")
            line("                ret += len;")
            line("            }")
            line("            return ret;")
            line("        }")
            line("    }")
            line("    throw new RuntimeException(\"syscall \" + syscall);")
            line("}")

            line("void printChar(int stream, int c) {")
            line("    switch (stream) {")
            line("        case 1: System.out.print((char)c); break;")
            line("        default: System.err.print((char)c); break;")
            line("    }")
            line("}")

            line("void __putBytes(int address, byte[] data) { for (int n = 0; n < data.length; n++) heap.put(address + n, data[n]); }")
            line("void __putBytes(int address, String data) { for (int n = 0; n < data.length(); n++) heap.put(address + n, (byte)data.charAt(n)); }")
            line("void __putBytesB64(int address, String... datas) { String out = \"\"; for (int n = 0; n < datas.length; n++) out += datas[n]; __putBytes(address, java.util.Base64.getDecoder().decode(out)); }")
            getImportGlobal("env", "DYNAMICTOP_PTR")?.let { line("int $it = DYNAMICTOP_PTR;") }
            getImportGlobal("env", "tempDoublePtr")?.let { line("int $it = 0;") }
            getImportGlobal("env", "tableBase")?.let { line("int $it = 0;") }
            getImportGlobal("env", "ABORT")?.let { line("int $it = 0;") }
            getImportGlobal("env", "STACKTOP")?.let { line("int $it = STACKTOP;") }
            getImportGlobal("env", "STACK_MAX")?.let { line("int $it = STACK_MAX;") }
            getImportGlobal("global", "NaN")?.let { line("double $it = java.lang.Double.NaN;") }
            getImportGlobal("global", "Infinity")?.let { line("double $it = java.lang.Double.POSITIVE_INFINITY;") }
            getImportFunc("env", "enlargeMemory")?.let { line("int $it() { throw new RuntimeException(); }") }
            getImportFunc("env", "abortOnCannotGrowMemory")?.let { line("void $it() { throw new RuntimeException(\"abortOnCannotGrowMemory\"); }") }
            getImportFunc("env", "abortStackOverflow")?.let { line("void $it(int arg) { throw new RuntimeException(\"abortStackOverflow\"); }") }
            getImportFunc("env", "_abort")?.let { line("void $it() { throw new RuntimeException(\"ABORT \"); }") }
            getImportFunc("env", "getTotalMemory")?.let { line("int $it() { return heap.limit(); }") }
            getImportFunc("env", "nullFunc_ii")?.let { line("int $it(int v) { return 0; }") }
            getImportFunc("env", "nullFunc_iiii")?.let { line("int $it(int a) { return 0; }") }
            getImportFunc("env", "___lock")?.let { line("void $it(int addr) {}") }
            getImportFunc("env", "___unlock")?.let { line("void $it(int addr) {}") }
            getImportFunc("env", "_emscripten_memcpy_big")?.let { line("int $it(int a, int b, int c) { throw new RuntimeException(); }") }
            getImportFunc("env", "___setErrNo")?.let { line("void $it(int errno) {}") }
            getImportFunc("env", "___syscall6")?.let { line("int $it(int syscall, int varargbuf) { throw new RuntimeException(); }") } //    SYS_close: 6,
            getImportFunc("env", "___syscall54")?.let { line("int $it(int syscall, int varargbuf) { throw new RuntimeException(); }") } //    SYS_ioctl: 54,
            getImportFunc("env", "___syscall140")?.let { line("int $it(int syscall, int varargbuf) { throw new RuntimeException(); }") } //    SYS__llseek: 140,
            getImportFunc("env", "___syscall146")?.let { line("int $it(int syscall, int varargbuf) { throw new RuntimeException(); }") } //    SYS_writev: 146,

            line("void _abort(int value) { throw new RuntimeException(\"ABORT \" + value); }")
            line("void abort(int value) { throw new RuntimeException(\"ABORT \" + value); }")
            line("void abortStackOverflow(int count) { throw new RuntimeException(\"abortStackOverflow(\$count)\"); }")

            line("void putBytes(int address, byte[] bytes, int offset, int size) {")
            line("    heap.position(address);")
            line("    heap.put(bytes, offset, size);")
            line("}")

            line("void putBytes(int address, byte[] bytes) { putBytes(address, bytes, 0, bytes.length); }")

            line("byte[] getBytes(int address, int size) {")
            line("    byte[] out = new byte[size];")
            line("    heap.position(address);")
            line("    heap.get(out);")
            line("    return out;")
            line("}")

            line("void putString(int address, String string, java.nio.charset.Charset charset) {")
            line("    putBytes(address, string.getBytes(charset));")
            line("}")

            line("byte[] getBytez(int address) { return getBytez(java.lang.Integer.MAX_VALUE); }")

            line("byte[] getBytez(int address, int max) {")
            line("    byte[] bytes = new byte[1024];")
            line("    int bpos = 0;")
            line("    for (int n = 0; n < max; n++) {")
            line("        int v = lbu(address + n);")
            line("        if (v == 0) break;")
            line("        if (bpos >= bytes.length - 1) {")
            line("            bytes = java.util.Arrays.copyOf(bytes, bytes.length * 3);")
            line("        }")
            line("        bytes[bpos++] = (byte)v;")
            line("    }")
            line("    return java.util.Arrays.copyOf(bytes, bpos);")
            line("}")

            line("String getStringz(int address, java.nio.charset.Charset charset) { return new java.lang.String(getBytez(address), charset); }")

            // Additions
            line("void ___assert_fail(int a, int b, int c, int d) { throw new RuntimeException(\"___assert_fail \" + a); }")
            line("void _exit(int a) { System.exit(a); }")
            line("double Op_f64_promote_f32(float v) { return (double)v; }")
            line("float Op_f32_demote_f64(double v) { return (float)v; }")
            line("int Op_i32_clz(int v) { return java.lang.Integer.numberOfLeadingZeros(v); }")
            line("int Op_i32_load16_s(int address, int offset, int align) { return (int)heap.getShort(checkAddress(address, offset)); }")
            line("int Op_i32_load16_u(int address, int offset, int align) { return (int)heap.getShort(checkAddress(address, offset)) & 0xFFFF; }")

            line("float Op_f32_neg(float v) { return (-v); }")
            line("float Op_f32_add(float l, float r) { return (l + r); }")
            line("float Op_f32_sub(float l, float r) { return (l - r); }")
            line("float Op_f32_mul(float l, float r) { return (l * r); }")
            line("float Op_f32_div(float l, float r) { return (l / r); }")
            line("float Op_f32_rem(float l, float r) { return (l % r); }")

            line("int Op_f32_eq(float l, float r) { return b2i(l == r); }")
            line("int Op_f32_ne(float l, float r) { return b2i(l != r); }")
            line("int Op_f32_lt(float l, float r) { return b2i(l < r); }")
            line("int Op_f32_le(float l, float r) { return b2i(l <= r); }")
            line("int Op_f32_gt(float l, float r) { return b2i(l > r); }")
            line("int Op_f32_ge(float l, float r) { return b2i(l >= r); }")
            line("int Op_f32_convert_u_i32(float v) { return (int)v; } // @TODO: Fixme!")

            // additional
            line("double Op_f64_abs(double v) { return java.lang.Math.abs(v); }")
            line("int Op_i32_trunc_s_f32(float v) { return (int)v; }")

            line("float Op_f32_convert_s_i32(int v) { return (float)v; }")

            // stdlib
            val importedFunctions = module.functions.map { it.name }.toSet()
            if ("_time" in importedFunctions) {
                line("int _time(int addr) { int time = (int)(System.currentTimeMillis() / 1000L); if (addr != 0) sw(addr, time); return time; }")
            }

            for (func in functionsWithImport - handledFunctionsWithImport) {
                println("Un-imported function ${func.import}")
            }

            val indices = LinkedHashMap<Int, String>()
            for (data in module.datas) {
                val ast = data.toAst(module)
                if (ast is Wast.RETURN && ast.expr is Wast.Const) {
                    indices[data.index] = "${ast.expr.value}"
                } else {
                    line("private int computeDataIndex${data.index}()") {
                        line(ast.dump(DumpContext(moduleCtx, null)).indenter)
                    }
                    indices[data.index] = "computeDataIndex${data.index}()"
                }
            }
            val initBlockSize = 16
            line("public Module()") {
                for (nn in 0 until module.datas.size step initBlockSize) {
                    line("init_$nn();")
                }
            }
            for (nn in 0 until module.datas.size step initBlockSize) {
                line("private void init_$nn()") {
                    for (mm in 0 until initBlockSize) {
                        val n = nn + mm
                        val data = module.datas.getOrNull(n) ?: break
                        val base64 = Base64.getEncoder().encodeToString(data.data)
                        val chunks = base64.splitInChunks(32 * 1024).map { "\"$it\"" }
                        line("__putBytesB64(${indices[data.index]}, ${chunks.joinToString(", ")});")
                    }
                }
            }
            for (global in module.globals) {
                if (global.import == null) {
                    val computeName = "compute${moduleCtx.getName(global)}"
                    line("private ${global.globalType.type.type()} $computeName()") {
                        val getterType = WasmType.Function(listOf(), listOf(global.globalType.type))
                        val ast: Wast.Stm = when {
                            global.e != null -> {
                                global.e.toAst(module, WasmFunc(-1, getterType))
                            }
                            global.ast != null -> global.ast
                            else -> TODO("Both ${global::e.name} and ${global::ast.name} are null")
                        }

                        line(ast.dump(DumpContext(moduleCtx, null)).indenter)
                    }
                    line("${global.globalType.type.type()} ${moduleCtx.getName(global)} = $computeName();")
                }
            }

            //fun WasmType.Function.typeName(): String = "Func${this.signature.toUpperCase()}"

            //for ((type, functions) in module.functions.values.groupBy { it.type }) {
            //    line("public interface ${type.typeName()}") {
            //        val args = type.args.withIndex().map { "${it.value.type()} v${it.index}" }.joinToString(", ")
            //        line("${type.retType.type()} invoke($args);")
            //    }
            //}

            //fun createDynamicFunction(type: WasmType.Function, name: String): String {
            //    val typeName = type.typeName()
            //    //return "this::${name}"
            //    return Indenter.genString {
            //        // Java8
            //        ///*
            //        line("new $typeName()") {
            //            val argsCall = type.args.withIndex().map { "v${it.index}" }.joinToString(", ")
            //            line("public ${type.retType.type()} invoke($args)") {
            //                val expr = "Module.this.$name($argsCall)"
            //                if (type.retTypeVoid) {
            //                    line("$expr;")
            //                } else {
            //                    line("return $expr;")
            //                }
            //            }
            //        }
            //        //*/
            //        //line("null")
            //    }
            //}

            //val funcs = module.functions.values.joinToString(", ") { "this::${it.name}" }
            val funcToIdx = module.elements.flatMap { it.funcRefs }.withIndex()
                .map { (module.getFunction(it.value) ?: invalidOp("Invalid referenced function $it")) to it.index }
                .toMap()
            //.joinToString(", ") { createDynamicFunction(it.type, it.name) }
            //line("Object[] functions = new Object[] { $funcs };")

            //println(funcToIdx)

            for ((type, functions) in module.functions.groupBy { it.type.withoutArgNames() }) {
                //val funcs = functions.joinToString(", ") { "this::${it.name}" }
                //line("val functions${type.signature} = arrayOf($funcs)")
                //val funcType = type.typeName()
                val ctx = DumpContext(moduleCtx, WasmFunc(-1, WasmType.Function(listOf(), listOf())))
                val args = type.args.map { "${it.type.type()} ${ctx.getName(it)}" }.joinToString(", ")
                val argsCall = type.args.withIndex().map { ctx.getName(it.value) }.joinToString(", ")
                val rfuncs = functions.map { it to funcToIdx[it] }.filter { it.second != null }
                val argsWithIndex = if (args.isEmpty()) "int index" else "int index, $args"
                line("${type.retType.type()} invoke_${type.signature}($argsWithIndex)") {
                    if (rfuncs.isNotEmpty()) {
                        line("switch (index)") {
                            for ((func, funcIdx) in rfuncs) {
                                if (func.type.retTypeVoid) {
                                    line("case $funcIdx: this.${func.name}($argsCall); return;")
                                } else {
                                    line("case $funcIdx: return this.${func.name}($argsCall);")
                                }
                            }
                        }
                    }
                    line("throw new RuntimeException(\"Invalid function at index \" + index);")
                }
            }

            for (func in module.functions) {
                line("// func (${func.index}) : ${func.name}")
                line(dump(func))
            }
        }
    }

    fun dump(func: WasmFunc): Indenter = Indenter {
        val bodyAst = func.getAst(module)
        if (bodyAst != null) {
            val visibility = if (func.export != null) "public " else "private "
            val ctx = DumpContext(moduleCtx, func)
            val args = func.type.args.joinToString(", ") { "${it.type.type()} " + ctx.getName(it) + "" }
            line("$visibility${func.type.retType.type()} ${moduleCtx.getName(func)}($args)") {
                //for ((index, local) in func.rlocals.withIndex()) {
                //    val value = if (index < func.type.args.size) "p$index" else local.default()
                //    line("${local.type()} l$index = $value;")
                //}
                val argsSet = func.type.args.toSet()
                for (local in bodyAst.getLocals()) {
                    if (local in argsSet) continue
                    //if (local.index >= MIN_TEMP_VARIABLE) {
                        line("${local.type.type()} ${ctx.getName(local)} = ${local.type.default()};")
                    //}
                }
                val res = bodyAst.dump(ctx)
                for (phi in ctx.phiTypes) line("${phi.type()} phi_$phi = ${phi.default()};")
                line(res.indenter)
            }
        }
    }


}

open class BaseJavaExporter : Exporter {
    companion object {
        val JAVA_KEYWORDS = setOf("do", "while", "if", "else", "void", "int", "this") // ...
        val PHI_NAMES = setOf("phi_i32", "phi_i64", "phi_f32", "phi_f64")
        val RESERVED_LOCALS = setOf("index")
        val JAVA_DEFINED_NAMES: Set<String> = JAVA_KEYWORDS + PHI_NAMES + RESERVED_LOCALS

        fun JavaNameAllocator() = NameAllocator {
            //var res = it.replace('$', '_').replace('-', '_')
            var res = it.replace('-', '_')
            while (res in Companion.JAVA_DEFINED_NAMES) res = "_$res"
            res
        }
    }
    override fun dump(): Indenter {
        TODO()
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


    class ModuleDumpContext() {
        private val usedNames = JavaNameAllocator()
        private val globalNames = LinkedHashMap<AstGlobal, String>()
        private val functionNames = LinkedHashMap<String, String>()

        fun getName(func: WasmFuncRef): String = functionNames.getOrPut(func.name) { usedNames.allocate(func.name) }
        //fun getName(func: WasmFunc): String = getName(func.ftype)
        fun getName(global: AstGlobal): String = globalNames.getOrPut(global) { usedNames.allocate(global.name) }
        fun getName(global: Wasm.WasmGlobal): String = getName(global.astGlobal)
    }

    class DumpContext(val moduleCtx: ModuleDumpContext, val func: WasmFunc?) {
        val phiTypes = LinkedHashSet<WasmType>()
        val debug get() = false
        //val debug get() = func?.name == "_memset"
        val usedNames = JavaNameAllocator()
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
            is Wast.SetLocal -> out.line("${ctx.getName(local)} = ${this.expr.dump(ctx)};")
            is Wast.SetGlobal -> out.line("this.${ctx.getName(global)} = ${this.expr.dump(ctx)};")
            is Wast.RETURN -> {
                out.line("return ${this.expr.dump(ctx)};")
                unreachable = true
            }
            is Wast.RETURN_VOID -> {
                out.line("return;")
                unreachable = true
            }
            is Wast.BLOCK -> {
                lateinit var result: DumpResult
                val optLabel = if (label != null) "${ctx.getName(label)}: " else ""
                out.line("${optLabel}do") {
                    result = this.stm.dump(ctx, out).appendBreaks(breaks)
                }

                out.line("while (false);")
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
                out.line("if (${this.cond.dump(ctx)} != 0)") {
                    val result = this.btrue.dump(ctx, out).appendBreaks(breaks)
                }
            }
            is Wast.IF_ELSE -> {
                out.line("if (${this.cond.dump(ctx)} != 0)") {
                    val result = this.btrue.dump(ctx, out).appendBreaks(breaks)
                }
                out.line("else") {
                    val result = this.bfalse.dump(ctx, out).appendBreaks(breaks)
                }
            }
            is Wast.BR -> {
                out.line(this.label.goto(ctx) + ";")
                breaks.addLabel(this.label)
                unreachable = true
            }
            is Wast.BR_IF -> {
                out.line("if (${this.cond.dump(ctx)} != 0) ${this.label.goto(ctx)};")
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
                var exprStr = this.expr.dump(ctx)
                while (exprStr.startsWith("(") && exprStr.endsWith(")")) {
                    exprStr = exprStr.substring(1, exprStr.length - 1)
                }

                if (this.expr is Wast.Const || this.expr is Wast.Local || this.expr is Wast.Global) {
                    out.line("// $exprStr; // Not a statement")
                } else {
                    out.line("$exprStr;")
                }
            }
            is Wast.WriteMemory -> {
                //out.line("${this.access()} = ${this.value.dump()}")
                out.line("${this.op}(${this.address.dump(ctx)}, ${this.offset}, ${this.align}, ${this.value.dump(ctx)});")
            }
            is Wast.SetPhi -> {
                out.line("phi_${this.blockType} = ${this.value.dump(ctx)};")
            }
            is Wast.Unreachable -> {
                out.line("// Unreachable")
                unreachable = true
            }
            is Wast.NOP -> {
                out.line("// nop")
            }
            else -> out.line("??? $this")
        }
        return DumpResult(out, breaks, unreachable)
    }


    fun Wast.Expr.dump(ctx: DumpContext): String {
        return when (this) {
            is Wast.Const -> when (this.type) {
                WasmType.f32 -> "(${this.value}f)"
                WasmType.i64 -> "(${this.value}L)"
                else -> "(${this.value})"
            }
            is Wast.Local -> ctx.getName(local)
            is Wast.Global -> "this." + ctx.getName(global)
            is Wast.Unop -> {
                //"(" + " ${this.op.symbol} " + this.expr.dump() + ")"
                "$op(${this.expr.dump(ctx)})"
            }
            is Wast.Terop -> {
                //"(" + " ${this.op.symbol} " + this.expr.dump() + ")"
                "(((${this.cond.dump(ctx)}) != 0) ? (${this.etrue.dump(ctx)}) : (${this.efalse.dump(ctx)}))"
            }
            is Wast.Binop -> {
                val ld = l.dump(ctx)
                val rd = r.dump(ctx)
                when (op) {
                //Ops.Op_i32_add -> "($ld + $rd)"
                //Ops.Op_i32_sub -> "($ld - $rd)"
                    else -> "$op($ld, $rd)"
                }
            }
            is Wast.CALL -> {
                "this.${ctx.moduleCtx.getName(func)}(${this.args.joinToString(", ") { it.dump(ctx) }})"
            }
        //is A.CALL_INDIRECT -> "((Op_getFunction(${this.address.dump()}) as (${this.type.type()}))" + "(" + this.args.map { it.dump() }.joinToString(
            is Wast.CALL_INDIRECT -> "(invoke_${this.type.signature}(${this.address.dump(ctx)}, " + this.args.map { it.dump(ctx) }.joinToString(
                ", "
            ) + "))"
            is Wast.ReadMemory -> {
                //this.access()
                "${this.op}(${this.address.dump(ctx)}, ${this.offset}, ${this.align})"
            }
            is Wast.Phi -> {
                ctx.phiTypes += this.type
                "phi_${this.type}"
            }
            else -> "???($this)"
        }
    }

    fun WasmType.default(): String = when (this) {
        WasmType.i64 -> "0L"
        WasmType.f32 -> "0f"
        WasmType.f64 -> "0.0"
        else -> "0"
    }

    fun WasmType.type(): String = when (this) {
        WasmType.void -> "void"
        WasmType.i32 -> "int"
        WasmType.i64 -> "long"
        WasmType.f32 -> "float"
        WasmType.f64 -> "double"
        is WasmType.Function -> {
            "(${this.args.joinToString(", ") { it.type.type() }}) -> ${this.retType.type()}"
        }
        else -> "$this"
    }

    fun AstLabel.goto(ctx: DumpContext) = "${this.kind.keyword} ${ctx.getName(this)}"
}

fun ByteArray.chunks(chunkSize: Int): List<ByteArray> {
    val out = arrayListOf<ByteArray>()
    for (n in 0 until this.size step chunkSize) {
        out += this.sliceArray(n until min(n + chunkSize, this.size))
    }
    return out
}