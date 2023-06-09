package com.soywiz.wasm

import korlibs.io.lang.invalidOp

enum class WasmOp(
    val id: Int,
    val sname: String,
    val istack: Int = -1,
    val rstack: Int = -1,
    val symbol: String = "<?>",
    val outType: WasmType = WasmType.void,
    val kind: Kind
) {
    //Op_i32(0x7f),
    //Op_i64(0x7e),
    //Op_f32(0x7d),
    //Op_f64(0x7c),
    //Op_anyfunc(0x70),
    //Op_func(0x60),
    //Op_empty(0x40),


    // Control flow operators
    Op_unreachable(0x00, "unreachable", kind = Kind.FLOW),
    Op_nop(0x01, "nop", kind = Kind.FLOW),
    Op_block(0x02, "block", kind = Kind.FLOW),
    Op_loop(0x03, "loop", kind = Kind.FLOW),
    Op_if(0x04, "if", kind = Kind.FLOW),
    Op_else(0x05, "else", kind = Kind.FLOW),
    Op_end(0x0b, "end", kind = Kind.FLOW),
    Op_br(0x0c, "br", kind = Kind.FLOW),
    Op_br_if(0x0d, "br_if", kind = Kind.FLOW),
    Op_br_table(0x0e, "br_table", kind = Kind.FLOW),
    Op_return(0x0f, "return", kind = Kind.FLOW),

    // Call operators
    Op_call(0x10, "call", -1, kind = Kind.CALL),
    Op_call_indirect(0x11, "call_indirect", -1, kind = Kind.CALL),

    // Parametric operators
    Op_drop(0x1a, "drop", 1, 0, kind = Kind.DROP),
    Op_select(0x1b, "select", 3, 1, kind = Kind.TEROP),

    // Variable access
    Op_get_local(0x20, "get_local", kind = Kind.LOCAL_GLOBAL),
    Op_set_local(0x21, "set_local", 1, kind = Kind.LOCAL_GLOBAL),
    Op_tee_local(0x22, "tee_local", kind = Kind.LOCAL_GLOBAL),
    Op_get_global(0x23, "get_global", kind = Kind.LOCAL_GLOBAL),
    Op_set_global(0x24, "set_global", 1, kind = Kind.LOCAL_GLOBAL),


    // Memory-related operators
    Op_i32_load(0x28, "i32.load", 1, 1, outType = WasmType.i32, kind = Kind.MEMORY_LOAD),
    Op_i64_load(0x29, "i64.load", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),
    Op_f32_load(0x2a, "f32.load", 1, 1, outType = WasmType.f32, kind = Kind.MEMORY_LOAD),
    Op_f64_load(0x2b, "f64.load", 1, 1, outType = WasmType.f64, kind = Kind.MEMORY_LOAD),

    Op_i32_load8_s(0x2c, "i32.load8_s", 1, 1, outType = WasmType.i32, kind = Kind.MEMORY_LOAD),
    Op_i32_load8_u(0x2d, "i32.load8_u", 1, 1, outType = WasmType.i32, kind = Kind.MEMORY_LOAD),
    Op_i32_load16_s(0x2e, "i32.load16_s", 1, 1, outType = WasmType.i32, kind = Kind.MEMORY_LOAD),
    Op_i32_load16_u(0x2f, "i32.load16_u", 1, 1, outType = WasmType.i32, kind = Kind.MEMORY_LOAD),

    Op_i64_load8_s(0x30, "i64.load8_s", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),
    Op_i64_load8_u(0x31, "i64.load8_u", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),
    Op_i64_load16_s(0x32, "i64.load16_s", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),
    Op_i64_load16_u(0x33, "i64.load16_u", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),
    Op_i64_load32_s(0x34, "i64.load32_s", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),
    Op_i64_load32_u(0x35, "i64.load32_u", 1, 1, outType = WasmType.i64, kind = Kind.MEMORY_LOAD),

    Op_i32_store(0x36, "i32.store", 2, 0, kind = Kind.MEMORY_STORE),
    Op_i64_store(0x37, "i64.store", 2, 0, kind = Kind.MEMORY_STORE),
    Op_f32_store(0x38, "f32.store", 2, 0, kind = Kind.MEMORY_STORE),
    Op_f64_store(0x39, "f64.store", 2, 0, kind = Kind.MEMORY_STORE),
    Op_i32_store8(0x3a, "i32.store8", 2, 0, kind = Kind.MEMORY_STORE),
    Op_i32_store16(0x3b, "i32.store16", 2, 0, kind = Kind.MEMORY_STORE),
    Op_i64_store8(0x3c, "i64.store8", 2, 0, kind = Kind.MEMORY_STORE),
    Op_i64_store16(0x3d, "i64.store16", 2, 0, kind = Kind.MEMORY_STORE),
    Op_i64_store32(0x3e, "i64.store32", 2, 0, kind = Kind.MEMORY_STORE),

    Op_memory_size(0x3f, "memory.size", kind = Kind.MEMORY_OP),
    Op_memory_grow(0x40, "memory.grow", kind = Kind.MEMORY_OP),

    // Constants opcodes
    Op_i32_const(0x41, "i32.const", 0, 1, outType = WasmType.i32, kind = Kind.LITERAL),
    Op_i64_const(0x42, "i64.const", 0, 1, outType = WasmType.i64, kind = Kind.LITERAL),
    Op_f32_const(0x43, "f32.const", 0, 1, outType = WasmType.f32, kind = Kind.LITERAL),
    Op_f64_const(0x44, "f64.const", 0, 1, outType = WasmType.f64, kind = Kind.LITERAL),

    // Comparison operators unop
    Op_i32_eqz(0x45, "i32.eqz", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i64_eqz(0x50, "i64.eqz", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),

    // Comparison operators
    Op_i32_eq(0x46, "i32.eq", 2, 1, "==", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_ne(0x47, "i32.ne", 2, 1, "!=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_lt_s(0x48, "i32.lt_s", 2, 1, "<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_lt_u(0x49, "i32.lt_u", 2, 1, "<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_gt_s(0x4a, "i32.gt_s", 2, 1, ">", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_gt_u(0x4b, "i32.gt_u", 2, 1, ">", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_le_s(0x4c, "i32.le_s", 2, 1, "<=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_le_u(0x4d, "i32.le_u", 2, 1, "<=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_ge_s(0x4e, "i32.ge_s", 2, 1, ">=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_ge_u(0x4f, "i32.ge_u", 2, 1, ">=", outType = WasmType.i32, kind = Kind.BINOP),

    Op_i64_eq(0x51, "i64.eq", 2, 1, "==", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_ne(0x52, "i64.ne", 2, 1, "!=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_lt_s(0x53, "i64.lt_s", 2, 1, "<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_lt_u(0x54, "i64.lt_u", 2, 1, "<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_gt_s(0x55, "i64.gt_s", 2, 1, ">", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_gt_u(0x56, "i64.gt_u", 2, 1, ">", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_le_s(0x57, "i64.le_s", 2, 1, "<=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_le_u(0x58, "i64.le_u", 2, 1, "<=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_ge_s(0x59, "i64.ge_s", 2, 1, ">=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i64_ge_u(0x5a, "i64.ge_u", 2, 1, ">=", outType = WasmType.i32, kind = Kind.BINOP),

    Op_f32_eq(0x5b, "f32.eq", 2, 1, "==", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f32_ne(0x5c, "f32.ne", 2, 1, "!=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f32_lt(0x5d, "f32.lt", 2, 1, "<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f32_gt(0x5e, "f32.gt", 2, 1, ">", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f32_le(0x5f, "f32.le", 2, 1, "<=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f32_ge(0x60, "f32.ge", 2, 1, ">=", outType = WasmType.i32, kind = Kind.BINOP),

    Op_f64_eq(0x61, "f64.eq", 2, 1, "==", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f64_ne(0x62, "f64.ne", 2, 1, "!=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f64_lt(0x63, "f64.lt", 2, 1, "<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f64_gt(0x64, "f64.gt", 2, 1, ">", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f64_le(0x65, "f64.le", 2, 1, "<=", outType = WasmType.i32, kind = Kind.BINOP),
    Op_f64_ge(0x66, "f64.ge", 2, 1, ">=", outType = WasmType.i32, kind = Kind.BINOP),


    // Numeric operators

    // int unary
    Op_i32_clz(0x67, "i32.clz", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i32_ctz(0x68, "i32.ctz", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i32_popcnt(0x69, "i32.popcnt", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),

    // int binary
    Op_i32_add(0x6a, "i32.add", 2, 1, "+", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_sub(0x6b, "i32.sub", 2, 1, "-", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_mul(0x6c, "i32.mul", 2, 1, "*", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_div_s(0x6d, "i32.div_s", 2, 1, "/", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_div_u(0x6e, "i32.div_u", 2, 1, "/", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_rem_s(0x6f, "i32.rem_s", 2, 1, "%", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_rem_u(0x70, "i32.rem_u", 2, 1, "%", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_and(0x71, "i32.and", 2, 1, "&", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_or(0x72, "i32.or", 2, 1, "|", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_xor(0x73, "i32.xor", 2, 1, "^", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_shl(0x74, "i32.shl", 2, 1, "<<", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_shr_s(0x75, "i32.shr_s", 2, 1, ">>", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_shr_u(0x76, "i32.shr_u", 2, 1, ">>>", outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_rotl(0x77, "i32.rotl", 2, 1, outType = WasmType.i32, kind = Kind.BINOP),
    Op_i32_rotr(0x78, "i32.rotr", 2, 1, outType = WasmType.i32, kind = Kind.BINOP),

    // long unary
    Op_i64_clz(0x79, "i64.clz", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i64_ctz(0x7a, "i64.ctz", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i64_popcnt(0x7b, "i64.popcnt", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),

    // long binary
    Op_i64_add(0x7c, "i64.add", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_sub(0x7d, "i64.sub", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_mul(0x7e, "i64.mul", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_div_s(0x7f, "i64.div_s", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_div_u(0x80, "i64.div_u", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_rem_s(0x81, "i64.rem_s", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_rem_u(0x82, "i64.rem_u", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_and(0x83, "i64.and", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_or(0x84, "i64.or", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_xor(0x85, "i64.xor", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_shl(0x86, "i64.shl", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_shr_s(0x87, "i64.shr_s", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_shr_u(0x88, "i64.shr_u", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_rotl(0x89, "i64.rotl", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),
    Op_i64_rotr(0x8a, "i64.rotr", 2, 1, outType = WasmType.i64, kind = Kind.BINOP),

    // float unary
    Op_f32_abs(0x8b, "f32.abs", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_neg(0x8c, "f32.neg", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_ceil(0x8d, "f32.ceil", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_floor(0x8e, "f32.floor", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_trunc(0x8f, "f32.trunc", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_nearest(0x90, "f32.nearest", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_sqrt(0x91, "f32.sqrt", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),

    // float binary
    Op_f32_add(0x92, "f32.add", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),
    Op_f32_sub(0x93, "f32.sub", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),
    Op_f32_mul(0x94, "f32.mul", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),
    Op_f32_div(0x95, "f32.div", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),
    Op_f32_min(0x96, "f32.min", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),
    Op_f32_max(0x97, "f32.max", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),
    Op_f32_copysign(0x98, "f32.copysign", 2, 1, outType = WasmType.f32, kind = Kind.BINOP),

    // double unary
    Op_f64_abs(0x99, "f64.abs", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_neg(0x9a, "f64.neg", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_ceil(0x9b, "f64.ceil", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_floor(0x9c, "f64.floor", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_trunc(0x9d, "f64.trunc", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_nearest(0x9e, "f64.nearest", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_sqrt(0x9f, "f64.sqrt", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),

    // double binary
    Op_f64_add(0xa0, "f64.add", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),
    Op_f64_sub(0xa1, "f64.sub", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),
    Op_f64_mul(0xa2, "f64.mul", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),
    Op_f64_div(0xa3, "f64.div", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),
    Op_f64_min(0xa4, "f64.min", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),
    Op_f64_max(0xa5, "f64.max", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),
    Op_f64_copysign(0xa6, "f64.copysign", 2, 1, outType = WasmType.f64, kind = Kind.BINOP),

    // Conversions
    Op_i32_wrap_i64(0xa7, "i32.wrap/i64", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i32_trunc_s_f32(0xa8, "i32.trunc_s/f32", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i32_trunc_u_f32(0xa9, "i32.trunc_u/f32", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i32_trunc_s_f64(0xaa, "i32.trunc_s/f64", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i32_trunc_u_f64(0xab, "i32.trunc_u/f64", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),

    Op_i64_extend_s_i32(0xac, "i64.extend_s/i32", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),
    Op_i64_extend_u_i32(0xad, "i64.extend_u/i32", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),
    Op_i64_trunc_s_f32(0xae, "i64.trunc_s/f32", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),
    Op_i64_trunc_u_f32(0xaf, "i64.trunc_u/f32", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),
    Op_i64_trunc_s_f64(0xb0, "i64.trunc_s/f64", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),
    Op_i64_trunc_u_f64(0xb1, "i64.trunc_u/f64", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),

    Op_f32_convert_s_i32(0xb2, "f32.convert_s/i32", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_convert_u_i32(0xb3, "f32.convert_u/i32", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_convert_s_i64(0xb4, "f32.convert_s/i64", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_convert_u_i64(0xb5, "f32.convert_u/i64", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f32_demote_f64(0xb6, "f32.demote/f64", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),

    Op_f64_convert_s_i32(0xb7, "f64.convert_s/i32", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_convert_u_i32(0xb8, "f64.convert_u/i32", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_convert_s_i64(0xb9, "f64.convert_s/i64", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_convert_u_i64(0xba, "f64.convert_u/i64", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),
    Op_f64_promote_f32(0xbb, "f64.promote/f32", 1, 1, outType = WasmType.f64, kind = Kind.UNOP),

    // Reinterpretations
    Op_i32_reinterpret_f32(0xbc, "i32.reinterpret/f32", 1, 1, outType = WasmType.i32, kind = Kind.UNOP),
    Op_i64_reinterpret_f64(0xbd, "i64.reinterpret/f64", 1, 1, outType = WasmType.i64, kind = Kind.UNOP),
    Op_f32_reinterpret_i32(0xbe, "f32.reinterpret/i32", 1, 1, outType = WasmType.f32, kind = Kind.UNOP),
    Op_f64_reinterpret_i64(0xbf, "f64.reinterpret/i64", 1, 1, outType = WasmType.f64, kind = Kind.UNOP);

    enum class Kind {
        FLOW,
        LITERAL,
        TEROP,
        BINOP,
        UNOP,
        DROP,
        MEMORY_LOAD,
        MEMORY_STORE,
        MEMORY_OP,
        LOCAL_GLOBAL,
        CALL
    }

    //val rname = name.removePrefix("Op_").replace('_', '.')

    init {
        check("Op_" + sname.replace('.', '_').replace('/', '_') == name)
    }

    companion object {
        val OPS_BY_ID = values().associateBy { it.id }
        val OPS_BY_SNAME = values().associateBy { it.sname }
        operator fun get(index: Int): WasmOp = OPS_BY_ID[index] ?: invalidOp("Invalid OP $index")
        operator fun get(name: String): WasmOp = OPS_BY_SNAME[name] ?: invalidOp("Invalid OP $name")
        operator fun invoke(index: Int): WasmOp = OPS_BY_ID[index] ?: invalidOp("Invalid OP $index")
        operator fun invoke(name: String): WasmOp = OPS_BY_SNAME[name]
                ?: invalidOp("Invalid OP $name")
    }
}
