package com.soywiz.wasm

interface WasmType {
    val id: Int
    val signature: String

    class _ARRAY(val element: WasmType) : WasmType {
        override val id = -1
        override fun toString() = "$element[]"
        override val signature: String = "[${element.signature}"
    }

    class _VARARG(val element: WasmType) : WasmType {
        override val id = -1
        override fun toString() = "$element..."
        override val signature: String = "[${element.signature}"
    }

    class _NULLABLE(val element: WasmType) : WasmType {
        override val id = -1
        override fun toString() = "$element"
        override val signature: String = "${element.signature}"
    }

    object void : WasmType {
        override val id = 0
        override fun toString() = "void"
        override val signature: String = "v"
    }

    object _boolean : WasmType {
        override val id = -1
        override fun toString() = "boolean"
        override val signature: String = "z"
    }

    object _i8 : WasmType {
        override val id = -1
        override fun toString() = "i8"
        override val signature: String = "b"
    }

    object _i16 : WasmType {
        override val id = -1
        override fun toString() = "i16"
        override val signature: String = "s"
    }

    object v128 : WasmType {
        override val id = 0
        override fun toString() = "Vector128"
        override val signature: String = "LVector128;"
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

    data class Function(val args: List<AstLocal>, val rets: List<WasmType>) : WasmType {
        override val id = -1

        init {
            check(rets.size <= 1) { "Multiple return values are not supported" }
        }

        val retType get() = rets.firstOrNull() ?: WasmType.void
        val retTypeVoid get() = retType == WasmType.void
        val argsPlusRet: List<WasmType> get() = args.map { it.type } + listOf(retType)
        override val signature: String get() = argsPlusRet.joinToString("") { it.signature }

        fun withoutArgNames(): Function = Function(
            args = args.withIndex().map { AstLocal(it.index, it.value.type) },
            rets = rets
        )

        override fun toString(): String = "(${args.joinToString(", ")}): $retType"
    }

    data class Global(val type: WasmType, val mutable: Boolean) : WasmType {
        override val id = -1
        override val signature: String = "g${type.signature}${if (mutable) "m" else "i"}"
    }

    data class TableType(val limit: Limit)

    companion object {
        operator fun invoke(str: String): WasmType {
            return when (str) {
                "i32" -> i32
                "i64" -> i64
                "f32" -> f32
                "f64" -> f64
                else -> TODO("Unsupported WasmType '$str'")
            }
        }
    }
}
