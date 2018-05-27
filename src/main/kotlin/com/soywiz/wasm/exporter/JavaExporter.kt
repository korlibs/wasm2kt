package com.soywiz.wasm.exporter

import com.soywiz.korio.error.*
import com.soywiz.korio.util.*
import com.soywiz.wasm.*
import com.soywiz.wasm.Indenter
import java.util.*
import kotlin.collections.LinkedHashMap

class JavaExporter(module: WasmModule) : Exporter(module) {
    override fun dump(config: ExportConfig): Indenter = Indenter {
        val names = JavaNameAllocator()
        val className = names.allocate(config.className)
        if (config.packageName.isNotEmpty()) {
            line("package ${config.packageName};")
        }
        line("public class $className") {
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

            line("static public final int heapSize = 64 * 1024 * 1024; // 64 MB")
            line("static public final int stackSize = 128 * 1024; // 128 KB ")

            line("public byte[] heapBytes = new byte[heapSize];")
            line("public java.nio.ByteBuffer heap = java.nio.ByteBuffer.wrap(heapBytes).order(java.nio.ByteOrder.nativeOrder());")

            writeOps(this)

            syscalls()
            missingSyscalls()

            line("void __putBytes(int address, byte[] data) { for (int n = 0; n < data.length; n++) heap.put(address + n, data[n]); }")
            line("void __putInts(int address, int[] data) { for (int n = 0; n < data.length; n++) heap.putInt(address + n * 4, data[n]); }")
            line("void __putBytes(int address, String data) { for (int n = 0; n < data.length(); n++) heap.put(address + n, (byte)data.charAt(n)); }")
            line("void __putBytesB64(int address, String... datas) { String out = \"\"; for (int n = 0; n < datas.length; n++) out += datas[n]; __putBytes(address, java.util.Base64.getDecoder().decode(out)); }")

            line("private int GLOBAL_BASE = 1024;")
            line("private int STACK_INITIAL_SIZE = stackSize;")
            val STACKTOP = getImportGlobal("env", "STACKTOP") ?: "STACKTOP"
            val STACK_MAX = getImportGlobal("env", "STACK_MAX") ?: "STACK_MAX"
            val maxmMemAlign = maxMem.nextAlignedTo(1024)
            //line("int $STACKTOP = $maxmMemAlign;")
            //line("int $STACK_MAX = $maxmMemAlign + STACK_INITIAL_SIZE;")

            line("private int $STACKTOP = heap.limit() - STACK_INITIAL_SIZE;")
            line("private int $STACK_MAX = heap.limit();")
            line("private int STACKTOP_REV = heap.limit();")

            line("public int stackAllocRev(int count) { STACKTOP_REV -= (count + 15) & ~0xF; return STACKTOP_REV; }")
            line("public int allocBytes(byte[] bytes) { int address = stackAllocRev(bytes.length); __putBytes(address, bytes); return address; }")
            line("public int allocInts(int[] ints) { int address = stackAllocRev(ints.length * 4); __putInts(address, ints); return address; }")
            line("public int allocStringz(String str) { try { return allocBytes((str + \"\\u0000\").getBytes(\"UTF-8\")); } catch (java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); } }")

            val DYNAMICTOP_PTR = getImportGlobal("env", "DYNAMICTOP_PTR") ?: "DYNAMICTOP_PTR"
            val tempDoublePtr = getImportGlobal("env", "tempDoublePtr") ?: "tempDoublePtr"

            line("int $DYNAMICTOP_PTR = 8;")
            line("int $tempDoublePtr = 16;")

            line("private void init_dynamictop()") {
                line("sw($DYNAMICTOP_PTR, $maxmMemAlign + 1024);")
                line("sw($tempDoublePtr, $maxmMemAlign);")
            }

            getImportGlobal("env", "tableBase")?.let { line("int $it = 0;") }
            getImportGlobal("env", "ABORT")?.let { line("int $it = -1;") }
            getImportGlobal("global", "NaN")?.let { line("double $it = java.lang.Double.NaN;") }
            getImportGlobal(
                "global",
                "Infinity"
            )?.let { line("double $it = java.lang.Double.POSITIVE_INFINITY;") }

            ifunc("global.Math", "pow", "double", "double a, double b") { line("return java.lang.Math.pow(a, b);") }

            ifunc("env", "getTotalMemory", "int", "") { line("""return heap.limit();""") }
            ifunc("env", "enlargeMemory", "int", "") { line("""return TODO_i32("enlargeMemory");""") }
            ifunc("env", "abortOnCannotGrowMemory", "void", "") { line("""TODO("abortOnCannotGrowMemory");""") }
            ifunc("env", "abortStackOverflow", "void", "int arg") { line("""TODO("abortStackOverflow");""") }
            ifunc("env", "abort", "void", "int v") { line("""TODO("ABORT: " + v);""") }
            ifunc("env", "_abort", "void", "") { line("TODO(\"ABORT\");") }
            ifunc("env", "nullFunc_i", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_ii", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_iii", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_iiii", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_v", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_vi", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_vii", "int", "int v") { line("return 0;") }
            ifunc("env", "nullFunc_viii", "int", "int v") { line("return 0;") }
            ifunc("env", "_exit", "void", "int code") { line("System.exit(code);") }
            ifunc("env", "___assert_fail", "void", "int a, int b, int c, int d") { line("TODO(\"___assert_fail\");") }
            ifunc("env", "___lock", "void", "int addr") { line("") }
            ifunc("env", "___unlock", "void", "int addr") { line("") }
            ifunc("env", "_emscripten_memcpy_big", "int", "int dst, int src, int count") {
                line("System.arraycopy(heapBytes, src, heapBytes, dst, count);")
                line("return dst;")
            }
            ifunc("env", "___setErrNo", "void", "int errno") { line("") }
            ifunc("env", "_time", "int", "int addr") {
                line("int time = (int)(System.currentTimeMillis() / 1000L);")
                line("if (addr != 0) sw(addr, time);")
                line("return time;")
            }

            line("public void putBytes(int address, byte[] bytes, int offset, int size) {")
            line("    heap.position(address);")
            line("    heap.put(bytes, offset, size);")
            line("}")

            line("public void putBytes(int address, byte[] bytes) { putBytes(address, bytes, 0, bytes.length); }")

            line("public byte[] getBytes(int address, int size) {")
            line("    byte[] out = new byte[size];")
            line("    heap.position(address);")
            line("    heap.get(out);")
            line("    return out;")
            line("}")

            line("public void putString(int address, String string, java.nio.charset.Charset charset) {")
            line("    putBytes(address, string.getBytes(charset));")
            line("}")

            line("public byte[] getBytez(int address) { return getBytez(address, java.lang.Integer.MAX_VALUE); }")

            line("public byte[] getBytez(int address, int max) {")
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

            line("public String getStringz(int address, java.nio.charset.Charset charset) { return new java.lang.String(getBytez(address), charset); }")
            line("public String getStringz(int address) { return getStringz(address, java.nio.charset.Charset.forName(\"UTF-8\")); }")

            for (func in functionsWithImport - handledFunctionsWithImport) {
                System.err.println("Un-imported function ${func.import}")
            }

            val initBlockSize = 16
            line("public $className()") {
                for (nn in 0 until module.datas.size step initBlockSize) {
                    line("init_$nn();")
                }
                line("init_dynamictop();")
            }
            for (nn in 0 until module.datas.size step initBlockSize) {
                line("private void init_$nn()") {
                    for (mm in 0 until initBlockSize) {
                        val n = nn + mm
                        val data = module.datas.getOrNull(n) ?: break
                        val base64 = Base64.getEncoder().encodeToString(data.data)
                        val chunks = base64.splitInChunks(32 * 1024).map { "\"$it\"" }
                        line("__putBytesB64(${dataIndices[data.index]}, ${chunks.joinToString(", ")});")
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

    fun Indenter.ifunc(ns: String, name: String, ret: String, args: String, callback: Indenter.() -> Unit) {
        getImportFunc(ns, name)?.let {
            line("$ret $it($args)") {
                callback()
            }
        }
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
                    //"\$_memmove", "\$_memcpy" -> {
                    //    val dst = ctx.getName(arg[0])
                    //    val src = ctx.getName(arg[1])
                    //    val count = ctx.getName(arg[2])
                    //    line("System.arraycopy(heapBytes, $src, heapBytes, $dst, $count);")
                    //    line("return $dst;")
                    //}
                    //"\$_memset" -> {
                    //    val ptr = ctx.getName(arg[0])
                    //    val value = ctx.getName(arg[1])
                    //    val num = ctx.getName(arg[2])
                    //    line("java.util.Arrays.fill(heapBytes, $ptr, $ptr + $num, (byte)$value);")
                    //    line("return $ptr;")
                    //}
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
            line("java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, smode);")
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
            line("java.io.RandomAccessFile raf = files.get(fd);")
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
            line("java.io.RandomAccessFile raf = files.get(fd);")
            line("int ret = 0;")
            line("end:")
            line("for (int cc = 0; cc < iovcnt; cc++)") {
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
            line("final int fd = lw(address + 0);")
            line("final int iov = lw(address + 4);")
            line("final int iovcnt = lw(address + 8);")
            //line("System.out.println(\"writev fd=\" + fd + \", iov=\" + iov + \", iovcnt=\" + iovcnt);")
            line("java.io.RandomAccessFile raf = files.get(fd);")
            line("int ret = 0;")
            line("for (int cc = 0; cc < iovcnt; cc++)") {
                line("int ptr = lw((iov + (cc * 8)) + 0);")
                line("int len = lw((iov + (cc * 8)) + 4);")
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

    override fun writeOps(i: Indenter): Unit = i.run {
        line("private int b2i(boolean v) { return v ? 1 : 0; }")

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

        line("private int Op_i32_load(int address, int offset, int align) { return heap.getInt(checkAddressRead(address, offset, align, 4)); }")
        line("private long Op_i64_load(int address, int offset, int align) { return heap.getLong(checkAddressRead(address, offset, align, 8)); }")
        line("private float Op_f32_load(int address, int offset, int align) { return heap.getFloat(checkAddressRead(address, offset, align, 4)); }")
        line("private double Op_f64_load(int address, int offset, int align) { return heap.getDouble(checkAddressRead(address, offset, align, 8)); }")
        line("private int Op_i32_load8_s(int address, int offset, int align) { return (int)heap.get(checkAddressRead(address, offset, align, 1)); }")
        line("private int Op_i32_load8_u(int address, int offset, int align) { return (int)heap.get(checkAddressRead(address, offset, align, 1)) & 0xFF; }")
        line("private int Op_i32_load16_s(int address, int offset, int align) { return (int)heap.getShort(checkAddressRead(address, offset, align, 2)); }")
        line("private int Op_i32_load16_u(int address, int offset, int align) { return (int)heap.getShort(checkAddressRead(address, offset, align, 2)) & 0xFFFF; }")
        line("private long Op_i64_load8_s(int address, int offset, int align) { return (long)heap.get(checkAddressRead(address, offset, align, 1)); }")
        line("private long Op_i64_load8_u(int address, int offset, int align) { return ((long)heap.get(checkAddressRead(address, offset, align, 1))) & 0xFFL; }")
        line("private long Op_i64_load16_s(int address, int offset, int align) { return (long)heap.getShort(checkAddressRead(address, offset, align, 2)); }")
        line("private long Op_i64_load16_u(int address, int offset, int align) { return (long)heap.getShort(checkAddressRead(address, offset, align, 2)) & 0xFFFFL; }")
        line("private long Op_i64_load32_s(int address, int offset, int align) { return (long)heap.getInt(checkAddressRead(address, offset, align, 4)); }")
        line("private long Op_i64_load32_u(int address, int offset, int align) { return (long)heap.getInt(checkAddressRead(address, offset, align, 4)) & 0xFFFFFFFFL; }")

        line("private void Op_i32_store(int address, int offset, int align, int value)") {
            //line("if (address + offset == 1109175052) System.out.println(\"write \" + address + \"=\" + value);")
            //line("System.out.println(\"write \" + address + \"=\" + value);")
            line("heap.putInt(checkAddressWrite(address, offset, align, 4), value);")
        }
        line("private void Op_i64_store(int address, int offset, int align, long value) { heap.putLong(checkAddressWrite(address, offset, align, 8), value); }")
        line("private void Op_f32_store(int address, int offset, int align, float value) { heap.putFloat(checkAddressWrite(address, offset, align, 4), value); }")
        line("private void Op_f64_store(int address, int offset, int align, double value) { heap.putDouble(checkAddressWrite(address, offset, align, 8), value); }")
        line("private void Op_i32_store8(int address, int offset, int align, int value) { heap.put(checkAddressWrite(address, offset, align, 1), (byte)value); }")
        line("private void Op_i32_store16(int address, int offset, int align, int value) { heap.putShort(checkAddressWrite(address, offset, align, 2), (short)value); }")
        line("private void Op_i64_store8(int address, int offset, int align, long value) { Op_i32_store8(address, offset, align, (int)value); }")
        line("private void Op_i64_store16(int address, int offset, int align, long value) { Op_i32_store16(address, offset, align, (int)value); }")
        line("private void Op_i64_store32(int address, int offset, int align, long value) { Op_i32_store(address, offset, align, (int)value); }")
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

        line("private int checkAddressRead(int address, int offset, int align, int size)") {
            line("return checkAddress(address, offset, align, size);")
        }
        line("")

        line("private int checkAddressWrite(int address, int offset, int align, int size)") {
            line("return checkAddress(address, offset, align, size);")
        }
        line("")

        line("private int Op_i32_eqz(int l)  { return b2i(l == 0); }")
        line("private int Op_i64_eqz(long l) { return b2i(l == 0L); }")

        line("private int Op_i32_eq(int l, int r      ) { return b2i(l == r); }")
        line("private int Op_i32_ne(int l, int r      ) { return b2i(l != r); }")
        line("private int Op_i32_lt_s(int l, int r    ) { return b2i(l < r); }")
        line("private int Op_i32_lt_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) < 0); }")
        line("private int Op_i32_gt_s(int l, int r    ) { return b2i(l > r); }")
        line("private int Op_i32_gt_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) > 0); }")
        line("private int Op_i32_le_s(int l, int r    ) { return b2i(l <= r); }")
        line("private int Op_i32_le_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) <= 0); }")
        line("private int Op_i32_ge_s(int l, int r    ) { return b2i(l >= r); }")
        line("private int Op_i32_ge_u(int l, int r    ) { return b2i(java.lang.Integer.compareUnsigned(l, r) >= 0); }")

        line("private int Op_i64_eq  (long l, long r  ) { return b2i(l == r); }")
        line("private int Op_i64_ne  (long l, long r  ) { return b2i(l != r); }")
        line("private int Op_i64_lt_s(long l, long r  ) { return b2i(l < r); }")
        line("private int Op_i64_lt_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) < 0); }")
        line("private int Op_i64_gt_s(long l, long r  ) { return b2i(l > r); }")
        line("private int Op_i64_gt_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) > 0); }")
        line("private int Op_i64_le_s(long l, long r  ) { return b2i(l <= r); }")
        line("private int Op_i64_le_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) <= 0); }")
        line("private int Op_i64_ge_s(long l, long r  ) { return b2i(l >= r); }")
        line("private int Op_i64_ge_u(long l, long r  ) { return b2i(java.lang.Long.compareUnsigned(l, r) >= 0); }")

        line("private int Op_f32_eq(float l, float r) { return b2i(l == r); }")
        line("private int Op_f32_ne(float l, float r) { return b2i(l != r); }")
        line("private int Op_f32_lt(float l, float r) { return b2i(l < r); }")
        line("private int Op_f32_gt(float l, float r) { return b2i(l > r); }")
        line("private int Op_f32_le(float l, float r) { return b2i(l <= r); }")
        line("private int Op_f32_ge(float l, float r) { return b2i(l >= r); }")

        line("private int Op_f64_eq(double l, double r) { return b2i(l == r); }")
        line("private int Op_f64_ne(double l, double r) { return b2i(l != r); }")
        line("private int Op_f64_lt(double l, double r) { return b2i(l < r); }")
        line("private int Op_f64_gt(double l, double r) { return b2i(l > r); }")
        line("private int Op_f64_le(double l, double r) { return b2i(l <= r); }")
        line("private int Op_f64_ge(double l, double r) { return b2i(l >= r); }")

        line("private int Op_i32_clz(int v) { return java.lang.Integer.numberOfLeadingZeros(v); }")
        line("private int Op_i32_ctz(int v) { return java.lang.Integer.numberOfTrailingZeros(v); }")
        line("private int Op_i32_popcnt(int v) { return java.lang.Integer.bitCount(v); }")

        line("private int Op_i32_add(int l, int r) { return l + r; }")
        line("private int Op_i32_sub(int l, int r) { return l - r; }")
        line("private int Op_i32_mul(int l, int r) { return l * r; }")
        line("private int Op_i32_div_s(int l, int r) { return l / r; }")
        line("private int Op_i32_div_u(int l, int r) { return java.lang.Integer.divideUnsigned(l, r); }")
        line("private int Op_i32_rem_s(int l, int r) { return l % r; }")
        line("private int Op_i32_rem_u(int l, int r) { return java.lang.Integer.remainderUnsigned(l, r); }")
        line("private int Op_i32_and(int l, int r) { return l & r; }")
        line("private int Op_i32_or(int l, int r) { return l | r; }")
        line("private int Op_i32_xor(int l, int r) { return l ^ r; }")
        line("private int Op_i32_shl(int l, int r) { return l << r; }")
        line("private int Op_i32_shr_s(int l, int r) { return l >> r; }")
        line("private int Op_i32_shr_u(int l, int r) { return l >>> r; }")
        line("private int Op_i32_rotl(int l, int r) { return java.lang.Integer.rotateLeft(l, r); }")
        line("private int Op_i32_rotr(int l, int r) { return java.lang.Integer.rotateRight(l, r); }")

        line("private int Op_i64_clz(long v) { return java.lang.Long.numberOfLeadingZeros(v); }")
        line("private int Op_i64_ctz(long v) { return java.lang.Long.numberOfTrailingZeros(v); }")
        line("private int Op_i64_popcnt(long v) { return java.lang.Long.bitCount(v); }")
        line("private long Op_i64_add(long l, long r) { return l + r; }")
        line("private long Op_i64_sub(long l, long r) { return l - r; }")
        line("private long Op_i64_mul(long l, long r) { return l * r; }")
        line("private long Op_i64_div_s(long l, long r) { return l / r; }")
        line("private long Op_i64_div_u(long l, long r) { return java.lang.Long.divideUnsigned(l, r); }")
        line("private long Op_i64_rem_s(long l, long r) { return l % r; }")
        line("private long Op_i64_rem_u(long l, long r) { return java.lang.Long.remainderUnsigned(l, r); }")
        line("private long Op_i64_and(long l, long r) { return l & r; }")
        line("private long Op_i64_or(long l, long r) { return l | r; }")
        line("private long Op_i64_xor(long l, long r) { return l ^ r; }")
        line("private long Op_i64_shl(long l, long r) { return l << (int)r; }")
        line("private long Op_i64_shr_s(long l, long r) { return l >> (int)r; }")
        line("private long Op_i64_shr_u(long l, long r) { return l >>> (int)r; }")
        line("private long Op_i64_rotl(long l, int r) { return java.lang.Long.rotateLeft(l, r); }")
        line("private long Op_i64_rotr(long l, int r) { return java.lang.Long.rotateRight(l, r); }")

        line("private float Op_f32_abs(float v) { return java.lang.Math.abs(v); }")
        line("private float Op_f32_neg(float v) { return (-v); }")
        line("private float Op_f32_ceil(float v) { return (float)java.lang.Math.ceil((double)v); }")
        line("private float Op_f32_floor(float v) { return (float)java.lang.Math.floor((double)v); }")
        line("private float Op_f32_trunc(float v) { TODO(); return (float)(long)(v); }") // @TODO: TODO
        line("private float Op_f32_nearest(float v) { TODO(); return (float)java.lang.Math.round((double)v); }") // @TODO: TODO
        line("private float Op_f32_sqrt(float v) { return (float)java.lang.Math.sqrt((double)v); }")

        line("private float Op_f32_add(float l, float r) { return (l + r); }")
        line("private float Op_f32_sub(float l, float r) { return (l - r); }")
        line("private float Op_f32_mul(float l, float r) { return (l * r); }")
        line("private float Op_f32_div(float l, float r) { return (l / r); }")
        line("private float Op_f32_min(float l, float r) { return java.lang.Math.min(l, r); }")
        line("private float Op_f32_max(float l, float r) { return java.lang.Math.min(l, r); }")
        line("private float Op_f32_copysign(float l, float r) { return java.lang.Math.copySign(l, r); }")

        line("private double Op_f64_abs(double v) { return java.lang.Math.abs(v); }")
        line("private double Op_f64_neg(double v) { return (-v); }")
        line("private double Op_f64_ceil(double v) { return java.lang.Math.ceil(v); }")
        line("private double Op_f64_floor(double v) { return java.lang.Math.floor(v); }")
        line("private double Op_f64_trunc(double v) { TODO(); return (double)(long)(v); }") // @TODO: TODO
        line("private double Op_f64_nearest(double v) { TODO(); return java.lang.Math.round(v); }") // @TODO: TODO

        line("private double Op_f64_sqrt(double v) { return java.lang.Math.sqrt(v); }")
        line("private double Op_f64_add(double l, double r) { return (l + r); }")
        line("private double Op_f64_sub(double l, double r) { return (l - r); }")
        line("private double Op_f64_mul(double l, double r) { return (l * r); }")
        line("private double Op_f64_div(double l, double r) { return (l / r); }")
        line("private double Op_f64_min(double l, double r) { return java.lang.Math.min(l, r); }")
        line("private double Op_f64_max(double l, double r) { return java.lang.Math.max(l, r); }")
        line("private double Op_f64_copysign(double l, double r) { return java.lang.Math.copySign(l, r); }")
        line("private int Op_i32_wrap_i64(long v)       { return (int)(v & 0xFFFFFFFFL); }")
        line("private int Op_i32_trunc_s_f32(float v) { return (int)v; }") // @TODO: VERIFY!
        line("private int Op_i32_trunc_u_f32(float v) { return (int)(long)v; }") // @TODO: VERIFY!

        line("private int Op_i32_trunc_s_f64(double v)") {
            line("if (v <= (double)java.lang.Integer.MIN_VALUE) return java.lang.Integer.MIN_VALUE;")
            line("if (v >= (double)java.lang.Integer.MAX_VALUE) return java.lang.Integer.MAX_VALUE;")
            line("return (int)v;")
        }
        line("private int Op_i32_trunc_u_f64(double v)") {
            line("if (v <= 0.0) return 0;")
            line("if (v >= 4294967296.0) return (int)4294967296L;")
            line("return (int)v;")
        }
        line("private long Op_i64_extend_s_i32(int v)   { return (long)v; }")
        line("private long Op_i64_extend_u_i32(int v)   { return (long)v & 0xFFFFFFFFL; }")

        line("private long Op_i64_trunc_s_f32(float v)   { TODO(); return (long)v; }") // @TODO: FIXME!
        line("private long Op_i64_trunc_u_f32(float v)   { TODO(); return (long)v; }") // @TODO: FIXME!

        line("private long Op_i64_trunc_s_f64(double v)   { return (long)v; }") // @TODO: FIXME!
        line("private long Op_i64_trunc_u_f64(double v)   { TODO(); return (long)v; }") // @TODO: FIXME!

        line("private float Op_f32_convert_s_i32(int v) { return (float)v; }")
        line("private float Op_f32_convert_u_i32(int v) { TODO(); return (float)((long)v & 0xFFFFFFFFL); } // @TODO: Fixme!") // @TODO: FIXME!
        line("private float Op_f32_convert_s_i64(long v) { TODO(); return (float)v; } // @TODO: Fixme!") // @TODO: FIXME!
        line("private float Op_f32_convert_u_i64(long v) { TODO(); return (float)v; } // @TODO: Fixme!") // @TODO: FIXME!

        line("private float  Op_f32_demote_f64   (double   v) { return (float)v; }")
        line("private double Op_f64_convert_s_i32(int      v) { return (double)v; }")
        line("private double Op_f64_convert_u_i32(int      v) { return (double)((long)v & 0xFFFFFFFFL); }")
        line("private double Op_f64_convert_s_i64(long     v) { return (double)v; }")
        line("private double Op_f64_convert_u_i64(long     v) { TODO(); return (double)v; } // @TODO: FIXME!") // @TODO: FIXME!
        line("private double Op_f64_promote_f32  (float    v) { return (double)v; }")
        line("private int    Op_i32_reinterpret_f32(float  v) { return java.lang.Float.floatToRawIntBits(v); }")
        line("private long   Op_i64_reinterpret_f64(double v) { return java.lang.Double.doubleToRawLongBits(v); }")
        line("private float  Op_f32_reinterpret_i32(int    v) { return java.lang.Float.intBitsToFloat(v); }")
        line("private double Op_f64_reinterpret_i64(long   v) { return java.lang.Double.longBitsToDouble(v); }")
    }
}
