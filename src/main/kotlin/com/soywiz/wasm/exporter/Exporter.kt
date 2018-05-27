package com.soywiz.wasm.exporter

import com.soywiz.wasm.*

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

        fun JavaNameAllocator() = NameAllocator {
            //var res = it.replace('$', '_').replace('-', '_')
            var res = it.replace('-', '_')
            while (res in JAVA_DEFINED_NAMES) res = "_$res"
            res
        }

        val O_RDONLY = 0x0000
        val O_WRONLY = 0x0001
        val O_RDWR = 0x0002
        val O_CREAT = 0x40
        val O_EXCL = 0x80
        val O_TRUNC = 0x200
        val O_APPEND = 0x400
        val O_DSYNC = 0x1000

        val i32 = WasmType.i32
        val i64 = WasmType.i64
        val f32 = WasmType.f32
        val f64 = WasmType.f64
    }

    val moduleCtx = ModuleDumpContext()
    val functionsWithImport = module.functions.filter { it.import != null }
    val functionsByImport = functionsWithImport.map { it.import!!.importPair to it }.toMap()
    val handledFunctionsWithImport = LinkedHashSet<WasmFunc>()

    val globalsWithImport = module.globals.filter { it.import != null }
    val globalsByImport = globalsWithImport.map { it.import!!.importPair to it }.toMap()

    val handledGlobalsWithImport = LinkedHashSet<Wasm.WasmGlobal>()

    fun cast_int(expr: String) = cast(i32, expr)
    fun cast_long(expr: String) = cast(i64, expr)
    fun cast_float(expr: String) = cast(f32, expr)
    fun cast_double(expr: String) = cast(f64, expr)

    inline fun Indenter.unop_int(op: WasmOp, expr: () -> String) = unop(op, i32, i32, expr())
    inline fun Indenter.unop_float(op: WasmOp, expr: () -> String) = unop(op, f32, f32, expr())
    inline fun Indenter.unop_double(op: WasmOp, expr: () -> String) = unop(op, f64, f64, expr())

    inline fun Indenter.binop_int_bool(op: WasmOp, expr: () -> String) = binop(op, i32, i32, "b2i(${expr()})")
    inline fun Indenter.binop_int(op: WasmOp, expr: () -> String) = binop(op, i32, i32, expr())

    inline fun Indenter.binop_long_bool(op: WasmOp, expr: () -> String) = binop(op, i64, i32, "b2i(${expr()})")
    inline fun Indenter.binop_long(op: WasmOp, expr: () -> String) = binop(op, i64, i64, expr())
    inline fun Indenter.binop_long_int(op: WasmOp, expr: () -> String) = binop(op, i64, i32, expr())

    inline fun Indenter.binop_float_bool(op: WasmOp, expr: () -> String) = binop(op, f32, i32, "b2i(${expr()})")
    inline fun Indenter.binop_float(op: WasmOp, expr: () -> String) = binop(op, f32, f32, expr())

    inline fun Indenter.binop_double_bool(op: WasmOp, expr: () -> String) = binop(op, f64, i32, "b2i(${expr()})")
    inline fun Indenter.binop_double(op: WasmOp, expr: () -> String) = binop(op, f64, f64, expr())

    inline fun Indenter.binop(op: WasmOp, arg: WasmType, ret: WasmType, expr: () -> String) = binop(op, arg, ret, expr())
    inline fun Indenter.unop(op: WasmOp, arg: WasmType, ret: WasmType, expr: () -> String) = unop(op, arg, ret, expr())

    open fun Indenter.binop(op: WasmOp, arg: WasmType, ret: WasmType, expr: String) = line("private ${ret.type()} $op(${arg.type()} l, ${arg.type()} r) { return $expr; }")
    open fun Indenter.unop(op: WasmOp, arg: WasmType, ret: WasmType, expr: String) = line("private ${ret.type()} $op(${arg.type()} v) { return $expr; }")
    open fun ternary(cond: String, strue: String, sfalse: String) = "(($cond) ? ($strue) : ($sfalse))"
    open fun cast(type: WasmType, expr: String) = "((${type.type()})($expr))"
    open val AND = "&"
    open val OR = "|"
    open val XOR = "^"
    open val SHL = "<<"
    open val SHR = ">>"
    open val SHRU = ">>>"

    // https://webassembly.github.io/spec/core/exec/numerics.html
    protected open fun Indenter.writeOps() {
    }

    protected open fun Indenter.writeBaseOps() {
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
        binop_long(WasmOp.Op_i64_rotl) { "java.lang.Long.rotateLeft(l, ${cast_int("r")})" }
        binop_long(WasmOp.Op_i64_rotr) { "java.lang.Long.rotateRight(l, ${cast_int("r")})" }

        unop_float(WasmOp.Op_f32_abs) { "java.lang.Math.abs(v)" }
        unop_float(WasmOp.Op_f32_neg) { "-v" }
        unop_float(WasmOp.Op_f32_ceil) { cast_float("java.lang.Math.ceil(${cast_double("v")})") }
        unop_float(WasmOp.Op_f32_floor) { cast_float("java.lang.Math.floor(${cast_double("v")})") }
        unop_float(WasmOp.Op_f32_trunc) { cast_float(cast_long("v")) } // @TODO: TODO
        unop_float(WasmOp.Op_f32_nearest) { cast_float("java.lang.Math.round(${cast_double("v")})") } // @TODO: TODO
        unop_float(WasmOp.Op_f32_sqrt) { cast_float("java.lang.Math.sqrt(${cast_double("v")})") }

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
        unop_double(WasmOp.Op_f64_trunc) { cast_double(cast_long("v")) } // @TODO: TODO
        unop_double(WasmOp.Op_f64_nearest) { "java.lang.Math.round(v)" } // @TODO: TODO
        unop_double(WasmOp.Op_f64_sqrt) { "java.lang.Math.sqrt(v)" }

        binop_double(WasmOp.Op_f64_add) { "l + r" }
        binop_double(WasmOp.Op_f64_sub) { "l - r" }
        binop_double(WasmOp.Op_f64_mul) { "l * r" }
        binop_double(WasmOp.Op_f64_div) { "l / r" }
        binop_double(WasmOp.Op_f64_min) { "java.lang.Math.min(l, r)" }
        binop_double(WasmOp.Op_f64_max) { "java.lang.Math.max(l, r)" }
        binop_double(WasmOp.Op_f64_copysign) { "java.lang.Math.copySign(l, r)" }

        unop(WasmOp.Op_i32_wrap_i64, i64, i32, cast_int("(v & 0xFFFFFFFFL)"))
        unop(WasmOp.Op_i32_trunc_s_f32, f32, i32, cast_int("v")) // @TODO: VERIFY!
        unop(WasmOp.Op_i32_trunc_u_f32, f32, i32, cast_int(cast_long("v"))) // @TODO: VERIFY!

        unop(WasmOp.Op_i32_trunc_s_f64, f64, i32) {
            ternary(
                "v <= ${cast_double("java.lang.Integer.MIN_VALUE")}",
                "java.lang.Integer.MIN_VALUE",
                ternary(
                    "v >= ${cast_double("java.lang.Integer.MAX_VALUE")}",
                    "java.lang.Integer.MAX_VALUE",
                    cast_int("v")
                )
            )
        }
        unop(WasmOp.Op_i32_trunc_u_f64, f64, i32) { ternary("v <= 0.0", "0", ternary("v >= 4294967296.0", cast_int("4294967296L"), cast_int("v"))) }
        unop(WasmOp.Op_i64_extend_s_i32, i32, i64) { cast_long("v") }
        unop(WasmOp.Op_i64_extend_u_i32, i32, i64) { "${cast_long("v")} $AND 0xFFFFFFFFL"  }

        unop(WasmOp.Op_i64_trunc_s_f32, f32, i64) { cast_long("v") } // @TODO: FIXME!
        unop(WasmOp.Op_i64_trunc_u_f32, f32, i64) { cast_long("v") } // @TODO: FIXME!

        unop(WasmOp.Op_i64_trunc_s_f64, f64, i64) { cast_long("v") } // @TODO: FIXME!
        unop(WasmOp.Op_i64_trunc_u_f64, f64, i64) { cast_long("v") } // @TODO: FIXME!

        unop(WasmOp.Op_f32_convert_s_i32, i32, f32) { cast_float("v") } // @TODO: FIXME!
        unop(WasmOp.Op_f32_convert_u_i32, i32, f32) { cast_float("${cast_long("v")} $AND 0xFFFFFFFFL") } // @TODO: FIXME!
        unop(WasmOp.Op_f32_convert_s_i64, i64, f32) { cast_float("v") } // @TODO: FIXME!
        unop(WasmOp.Op_f32_convert_u_i64, i64, f32) { cast_float("v") } // @TODO: FIXME!

        unop(WasmOp.Op_f32_demote_f64, f64, f32) { cast_float("v") }
        unop(WasmOp.Op_f64_promote_f32, f32, f64) { cast_double("v") }

        unop(WasmOp.Op_f64_convert_s_i32, i32, f64) { cast_double("v") }
        unop(WasmOp.Op_f64_convert_u_i32, i32, f64) { cast_double("${cast_long("v")} $AND 0xFFFFFFFFL") }
        unop(WasmOp.Op_f64_convert_s_i64, i64, f64) { cast_double("v") }
        unop(WasmOp.Op_f64_convert_u_i64, i64, f64) { cast_double("v") } // @TODO: FIXME!

        unop(WasmOp.Op_i32_reinterpret_f32, f32, i32) { "java.lang.Float.floatToRawIntBits(v)" }
        unop(WasmOp.Op_i64_reinterpret_f64, f64, i64) { "java.lang.Double.doubleToRawLongBits(v)" }
        unop(WasmOp.Op_f32_reinterpret_i32, i32, f32) { "java.lang.Float.intBitsToFloat(v)" }
        unop(WasmOp.Op_f64_reinterpret_i64, i64, f64) { "java.lang.Double.longBitsToDouble(v)" }
    }

    protected open fun Indenter.writeMain(className: String) {
    }

    fun Indenter.iglob(ns: String, name: String, ret: String, callback: () -> String) {
        getImportGlobal(ns, name)?.let {
            line("$ret $it = ${callback()};")
        }
    }

    fun Indenter.ifunc(ns: String, name: String, ret: String, args: String, callback: Indenter.() -> Unit) {
        getImportFunc(ns, name)?.let {
            line("$ret $it($args)") {
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

    open fun dump(config: ExportConfig): Indenter {
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
        fun getName(func: WasmFuncWithType): String =
            functionNames.getOrPut(func.name) { usedNames.allocate(func.name) }

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
        WasmOp.Op_f64_promote_f32 -> "((double)($vd))"
        WasmOp.Op_f32_demote_f64 -> "((float)($vd))"
        else -> "$op($vd)"
    }

    protected open fun binop(op: WasmOp, ld: String, rd: String) = when (op) {
        WasmOp.Op_i32_add, WasmOp.Op_i64_add, WasmOp.Op_f32_add, WasmOp.Op_f64_add -> "($ld + $rd)"
        WasmOp.Op_i32_sub, WasmOp.Op_i64_sub, WasmOp.Op_f32_sub, WasmOp.Op_f64_sub -> "($ld - $rd)"
        WasmOp.Op_i32_mul, WasmOp.Op_i64_mul, WasmOp.Op_f32_mul, WasmOp.Op_f64_mul -> "($ld * $rd)"
        WasmOp.Op_i32_div_s, WasmOp.Op_i64_div_s, WasmOp.Op_f32_div, WasmOp.Op_f64_div -> "($ld / $rd)"
        WasmOp.Op_i32_rem_s, WasmOp.Op_i64_rem_s -> "($ld % $rd)"
        WasmOp.Op_i32_and, WasmOp.Op_i64_and -> "($ld & $rd)"
        WasmOp.Op_i32_or, WasmOp.Op_i64_or -> "($ld | $rd)"
        WasmOp.Op_i32_xor, WasmOp.Op_i64_xor -> "($ld ^ $rd)"
        WasmOp.Op_i32_shl, WasmOp.Op_i64_shl -> "($ld << $rd)"
        WasmOp.Op_i32_shr_s, WasmOp.Op_i64_shr_s -> "($ld >> $rd)"
        WasmOp.Op_i32_shr_u, WasmOp.Op_i64_shr_u -> "($ld >>> $rd)"
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

    open fun WasmType.default(): String = when (this) {
        WasmType.i64 -> "0L"
        WasmType.f32 -> "0f"
        WasmType.f64 -> "0.0"
        else -> "0"
    }

    open fun WasmType.type(): String = when (this) {
        WasmType.void -> "void"
        WasmType.i32 -> "int"
        WasmType.i64 -> "long"
        WasmType.f32 -> "float"
        WasmType.f64 -> "double"
        is WasmType.Function -> "(${this.args.joinToString(", ") { it.type.type() }}) -> ${this.retType.type()}"
        else -> "$this"
    }

    fun AstLabel.goto(ctx: DumpContext) = "${this.kind.keyword} ${ctx.getName(this)}"
}
