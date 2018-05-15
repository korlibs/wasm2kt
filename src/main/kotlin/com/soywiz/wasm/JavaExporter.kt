package com.soywiz.wasm

import com.soywiz.korio.error.*
import com.soywiz.korio.util.*
import java.util.*
import kotlin.math.*

class JavaExporter(val wasm: Wasm) : Exporter {
    val module = wasm
    override fun dump(): Indenter = Indenter {
        line("public class Module") {
            val mainFunc = module.functions.values.firstOrNull { it.name == "_main" }
            if (mainFunc != null) {
                line("static public void main(String[] args)") {
                    when (mainFunc.type.args.size) {
                        0 -> line("new Module()._main();")
                        1 -> line("new Module()._main(0);")
                        2 -> line("new Module()._main(0, 0);")
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
            line("        System.out.println(\"ADDRESS: \$raddress (\$address + \$offset)\");")
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

            line("int _emscripten_memcpy_big(int a, int b, int c) { throw new RuntimeException(); }")
            line("int enlargeMemory() { throw new RuntimeException(); }")
            line("void __putBytes(int address, byte[] data) { for (int n = 0; n < data.length; n++) heap.put(address + n, data[n]); }")
            line("void __putBytes(int address, String data) { for (int n = 0; n < data.length(); n++) heap.put(address + n, (byte)data.charAt(n)); }")
            line("void __putBytesB64(int address, String... datas) { String out = \"\"; for (int n = 0; n < datas.length; n++) out += datas[n]; __putBytes(address, java.util.Base64.getDecoder().decode(out)); }")
            line("void _abort(int value) { throw new RuntimeException(\"ABORT \" + value); }")
            line("void abort(int value) { throw new RuntimeException(\"ABORT \" + value); }")
            line("void abortStackOverflow(int count) { throw new RuntimeException(\"abortStackOverflow(\$count)\"); }")
            line("void abortOnCannotGrowMemory() { throw new RuntimeException(\"abortOnCannotGrowMemory\"); }")
            line("int getTotalMemory() { return heap.limit(); }")
            line("void ___lock(int addr) {}")
            line("void ___unlock(int addr) {}")
            line("void ___setErrNo(int errno) {}")
            line("int nullFunc_ii(int v) { return 0; }")
            line("int nullFunc_iiii(int v) { return 0; }")

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

            val indices = LinkedHashMap<Int, String>()
            for (data in wasm.datas) {
                val ast = data.e.toAst(wasm, Wasm.WasmFunc(wasm, -1, INT_FUNC_TYPE))
                if (ast is A.RETURN && ast.expr is A.Const) {
                    indices[data.index] = "${ast.expr.value}"
                } else {
                    line("private int computeDataIndex${data.index}()") {
                        line(ast.dump())
                    }
                    indices[data.index] = "computeDataIndex${data.index}()"
                }
            }
            val initBlockSize = 16
            line("public Module()") {
                for (nn in 0 until wasm.datas.size step initBlockSize) {
                    line("init_$nn();")
                }
            }
            for (nn in 0 until wasm.datas.size step initBlockSize) {
                line("private void init_$nn()") {
                    for (mm in 0 until initBlockSize) {
                        val n = nn + mm
                        val data = wasm.datas.getOrNull(n) ?: break
                        val base64 = Base64.getEncoder().encodeToString(data.data)
                        val chunks = base64.splitInChunks(32 * 1024).map { "\"$it\"" }
                        line("__putBytesB64(${indices[data.index]}, ${chunks.joinToString(", ")});")
                    }
                }
            }
            for (global in module.globals.values) {
                if (global.import == null) {
                    line("private ${global.globalType.type.type()} compute${global.name}()") {
                        line(
                            global.e!!.toAst(
                                wasm,
                                Wasm.WasmFunc(
                                    wasm,
                                    -1,
                                    Wasm.WasmType.Function(listOf(), listOf(global.globalType.type))
                                )
                            ).dump()
                        )
                    }
                    line("${global.globalType.type.type()} ${global.name} = compute${global.name}();")
                }
            }

            //fun Wasm.WasmType.Function.typeName(): String = "Func${this.signature.toUpperCase()}"

            //for ((type, functions) in module.functions.values.groupBy { it.type }) {
            //    line("public interface ${type.typeName()}") {
            //        val args = type.args.withIndex().map { "${it.value.type()} v${it.index}" }.joinToString(", ")
            //        line("${type.retType.type()} invoke($args);")
            //    }
            //}

            //fun createDynamicFunction(type: Wasm.WasmType.Function, name: String): String {
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
            val funcToIdx = module.elements.flatMap { it.funcIdxs }
                .map { (module.functions[it] ?: invalidOp("Invalid referenced function $it")) to it }
                .toMap()
            //.joinToString(", ") { createDynamicFunction(it.type, it.name) }
            //line("Object[] functions = new Object[] { $funcs };")

            for ((type, functions) in module.functions.values.groupBy { it.type }) {
                //val funcs = functions.joinToString(", ") { "this::${it.name}" }
                //line("val functions${type.signature} = arrayOf($funcs)")
                //val funcType = type.typeName()
                val args = type.args.withIndex().map { "${it.value.type()} v${it.index}" }.joinToString(", ")
                val argsCall = type.args.withIndex().map { "v${it.index}" }.joinToString(", ")
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

            for (func in module.functions.values) {
                line("// func (${func.index}) : ${func.name}")
                line(dump(func))
            }
        }
    }

    fun dump(func: Wasm.WasmFunc): Indenter = Indenter {
        val code = func.code
        if (code != null) {
            val body = code.body
            val bodyAst = body.toAst(wasm, func)
            val visibility = if (func.export != null) "public " else "private "
            val args = func.type.args.withIndex().joinToString(", ") { "${it.value.type()} p" + it.index + "" }
            line("$visibility${func.type.retType.type()} ${func.name}($args)") {
                for ((index, local) in func.rlocals.withIndex()) {
                    val value = if (index < func.type.args.size) "p$index" else local.default()
                    line("${local.type()} l$index = $value;")
                }
                for (local in bodyAst.getLocals()) {
                    if (local.index >= MIN_TEMP_VARIABLE) {
                        line("${local.type.type()} ${local.name} = ${local.type.default()};")
                    }
                }
                line("int phi_i32 = 0;")
                line("long phi_i64 = 0L;")
                line("float phi_f32 = 0f;")
                line("double phi_f64 = 0.0;")
                line(bodyAst.dump())
            }
        }
    }

    fun A.Expr.dump(): String {
        return when (this) {
            is A.Const -> when (this.type) {
                Wasm.WasmType.f32 -> "(${this.value}f)"
                Wasm.WasmType.i64 -> "(${this.value}L)"
                else -> "(${this.value})"
            }
            is A.Local -> this.local.name
            is A.Global -> global.name
            is A.Unop -> {
                //"(" + " ${this.op.symbol} " + this.expr.dump() + ")"
                "$op(${this.expr.dump()})"
            }
            is A.Binop -> {
                val ld = l.dump()
                val rd = r.dump()
                when (op) {
                //Wasm.Ops.Op_i32_add -> "($ld + $rd)"
                //Wasm.Ops.Op_i32_sub -> "($ld - $rd)"
                    else -> "$op($ld, $rd)"
                }
            }
            is A.CALL -> {
                val name = if (this.func.name.startsWith("___syscall")) "___syscall" else this.func.name
                "$name(${this.args.map { it.dump() }.joinToString(", ")})"
            }
        //is A.CALL_INDIRECT -> "((Op_getFunction(${this.address.dump()}) as (${this.type.type()}))" + "(" + this.args.map { it.dump() }.joinToString(
            is A.CALL_INDIRECT -> "(invoke_${this.type.signature}(${this.address.dump()}, " + this.args.map { it.dump() }.joinToString(
                ", "
            ) + "))"
            is A.ReadMemory -> {
                //this.access()
                "${this.op}(${this.address.dump()}, ${this.offset}, ${this.align})"
            }
            is A.Phi -> "phi_${this.type}"
            else -> "???($this)"
        }
    }

    fun Wasm.WasmType.default(): String = when (this) {
        Wasm.WasmType.i64 -> "0L"
        Wasm.WasmType.f32 -> "0f"
        Wasm.WasmType.f64 -> "0.0"
        else -> "0"
    }

    fun Wasm.WasmType.type(): String = when (this) {
        Wasm.WasmType.void -> "void"
        Wasm.WasmType.i32 -> "int"
        Wasm.WasmType.i64 -> "long"
        Wasm.WasmType.f32 -> "float"
        Wasm.WasmType.f64 -> "double"
        is Wasm.WasmType.Function -> {
            "(${this.args.joinToString(", ") { it.type() }}) -> ${this.retType.type()}"
        }
        else -> "$this"
    }

    fun AstLabel.goto() = "${this.kind.keyword} ${this.name}"

    fun A.Stm.dump(out: Indenter = Indenter { }): Indenter {
        when (this) {
            is A.Stms -> {
                for (e in stms) {
                    e.dump(out)
                    if (e is A.Unreachable) break // Stop
                }
            }
            is A.AssignLocal -> out.line("${this.local.name} = ${this.expr.dump()};")
            is A.AssignGlobal -> out.line("${this.global.name} = ${this.expr.dump()};")
            is A.RETURN -> out.line("return ${this.expr.dump()};")
            is A.RETURN_VOID -> out.line("return;")
            is A.BLOCK -> {
                out.line("${label.name}: do") {
                    this.stm.dump(out)
                }
                out.line("while (false);")
            }
            is A.LOOP -> {
                out.line("${label.name}: while (true)") {
                    this.stm.dump(out)
                    if (this.stm.last() is A.Unreachable || this.stm.last() is A.BR) {
                        out.line("//break;")
                    } else {
                        out.line("break;")
                    }
                }
            }
            is A.IF -> {
                out.line("if (${this.cond.dump()} != 0)") {
                    this.btrue.dump(out)
                }
            }
            is A.IF_ELSE -> {
                out.line("if (${this.cond.dump()} != 0)") {
                    this.btrue.dump(out)
                }
                out.line("else") {
                    this.bfalse.dump(out)
                }
            }
            is A.BR -> {
                out.line(this.label.goto() + ";")
            }
            is A.BR_IF -> {
                out.line("if (${this.cond.dump()} != 0) ${this.label.goto()};")
            }
            is A.BR_TABLE -> {
                out.line("switch (${this.subject.dump()})") {
                    for ((index, label) in this.labels.withIndex()) {
                        out.line("case $index: ${label.goto()};")
                    }
                    out.line("default: ${this.default.goto()};")
                }
            }
            is A.STM_EXPR -> {
                var exprStr = this.expr.dump()
                while (exprStr.startsWith("(") && exprStr.endsWith(")")) exprStr =
                        exprStr.substring(1, exprStr.length - 1)
                if (this.expr is A.Const || this.expr is A.Local) {
                    out.line("// $exprStr")
                } else {
                    out.line("$exprStr;")
                }
            }
            is A.WriteMemory -> {
                //out.line("${this.access()} = ${this.value.dump()}")
                out.line("${this.op}(${this.address.dump()}, ${this.offset}, ${this.align}, ${this.value.dump()});")
            }
            is A.SetPhi -> {
                out.line("phi_${this.blockType} = ${this.value.dump()};")
            }
            is A.Unreachable -> {
                out.line("// Unreachable")
            }
            is A.NOP -> {
                out.line("// nop")
            }
            else -> out.line("??? $this")
        }
        return out
    }
}

fun ByteArray.chunks(chunkSize: Int): List<ByteArray> {
    val out = arrayListOf<ByteArray>()
    for (n in 0 until this.size step chunkSize) {
        out += this.sliceArray(n until min(n + chunkSize, this.size))
    }
    return out
}