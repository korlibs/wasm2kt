import com.soywiz.korio.stream.*
import java.nio.*
import java.nio.charset.*

open class WasmModule {
    //val heap = ByteBuffer.allocate(32 * 1024).order(ByteOrder.nativeOrder())
    //val heap = ByteBuffer.allocate(2048).order(ByteOrder.nativeOrder())
    val heap = ByteBuffer.allocate(16 * 1024 * 1024).order(ByteOrder.nativeOrder())

    @JvmField var ABORT = -1
    @JvmField var DYNAMICTOP_PTR = 0
    @JvmField var STACK_INITIAL_SIZE = 32 * 1024
    //@JvmField var STACK_INITIAL_SIZE = 15 * 1024 * 1024
    @JvmField var STACK_MAX = heap.limit()
    @JvmField var STACKTOP = STACK_MAX - STACK_INITIAL_SIZE

    fun allocStack(count: Int): Int {
        STACKTOP -= count
        return STACKTOP
    }
    @JvmField var tempDoublePtr = allocStack(1024)

    //@JvmField
    //var STACKTOP = 1712
    //@JvmField var STACKTOP = 16 * 1024

    @JvmField val NaN = Double.NaN
    @JvmField val Infinity = Double.POSITIVE_INFINITY

    fun Op_i32_add(l: Int, r: Int) = l + r
    fun Op_i32_sub(l: Int, r: Int) = l - r
    fun Op_i32_mul(l: Int, r: Int) = l * r
    fun Op_i32_div_u(l: Int, r: Int) = java.lang.Integer.divideUnsigned(l, r)
    fun Op_i32_div_s(l: Int, r: Int) = l / r
    fun Op_i32_rem_u(l: Int, r: Int) = java.lang.Integer.remainderUnsigned(l, r)
    fun Op_i32_rem_s(l: Int, r: Int) = l % r
    fun Op_i32_and(l: Int, r: Int) = l and r
    fun Op_i32_xor(l: Int, r: Int) = l xor r
    fun Op_i32_or(l: Int, r: Int) = l or r
    fun Op_i32_shr_u(l: Int, r: Int) = l ushr r
    fun Op_i32_shr_s(l: Int, r: Int) = l shr r
    fun Op_i32_shl(l: Int, r: Int) = l shl r

    fun Double.check(): Double {
        //if (this.isNaN() || this.isInfinite()) {
        //    println("DOUBLE: $this")
        //    println("DOUBLE: $this")
        //}
        return this
    }

    fun Op_i64_add(l: Long, r: Long) = l + r
    fun Op_i64_sub(l: Long, r: Long) = l - r
    fun Op_i64_mul(l: Long, r: Long) = l * r
    fun Op_i64_div_u(l: Long, r: Long) = java.lang.Long.divideUnsigned(l, r)
    fun Op_i64_div_s(l: Long, r: Long) = l / r
    fun Op_i64_rem_u(l: Long, r: Long) = java.lang.Long.remainderUnsigned(l, r)
    fun Op_i64_rem_s(l: Long, r: Long) = l % r
    fun Op_i64_and(l: Long, r: Long) = l and r
    fun Op_i64_xor(l: Long, r: Long) = l xor r
    fun Op_i64_or(l: Long, r: Long) = l or r
    fun Op_i64_shr_u(l: Long, r: Long) = l ushr r.toInt()
    fun Op_i64_shr_s(l: Long, r: Long) = l shr r.toInt()
    fun Op_i64_shl(l: Long, r: Long) = l shl r.toInt()

    fun Op_f64_neg(v: Double) = (-v.check()).check()
    fun Op_f64_add(l: Double, r: Double) = (l.check() + r.check()).check()
    fun Op_f64_sub(l: Double, r: Double) = (l.check() - r.check()).check()
    fun Op_f64_mul(l: Double, r: Double) = (l.check() * r.check()).check()
    fun Op_f64_div(l: Double, r: Double) = (l.check() / r.check()).check()
    fun Op_f64_rem(l: Double, r: Double) = (l.check() % r.check()).check()

    fun Op_i64_reinterpret_f64(v: Double) = java.lang.Double.doubleToRawLongBits(v)
    fun Op_f64_reinterpret_i64(v: Long) = (java.lang.Double.longBitsToDouble(v)).check()

    fun Op_i32_reinterpret_f32(v: Float) = java.lang.Float.floatToRawIntBits(v)
    fun Op_f32_reinterpret_i32(v: Int) = java.lang.Float.intBitsToFloat(v)

    fun Op_f64_convert_u_i32(v: Int) = (v.toLong() and 0xFFFFFFFFL).toDouble().check()
    fun Op_f64_convert_s_i32(v: Int) = v.toDouble().check()

    fun Op_i32_trunc_u_f64(v: Double): Int {
        v.check()
        if (v <= 0.0) return 0
        if (v >= 4294967296.0) return 4294967296L.toInt()
        return v.toInt()
    }
    fun Op_i32_trunc_s_f64(v: Double): Int {
        v.check()
        if (v <= Int.MIN_VALUE.toDouble()) return Int.MIN_VALUE
        if (v >= Int.MAX_VALUE.toDouble()) return Int.MAX_VALUE
        return v.toInt()
    }

    fun Op_i32_wrap_i64(v: Long): Int = (v and 0xFFFFFFFFL).toInt()
    fun Op_i64_extend_s_i32(v: Int): Long = v.toLong()
    fun Op_i64_extend_u_i32(v: Int): Long = v.toLong() and 0xFFFFFFFFL

    fun Boolean.toInt(): Int = if (this) 1 else 0

    fun Op_i32_eqz(l: Int): Int = (l == 0).toInt()
    fun Op_i32_eq(l: Int, r: Int): Int = (l == r).toInt()
    fun Op_i32_ne(l: Int, r: Int): Int = (l != r).toInt()
    fun Op_i32_lt_u(l: Int, r: Int): Int = (java.lang.Integer.compareUnsigned(l, r) < 0).toInt()
    fun Op_i32_gt_u(l: Int, r: Int): Int = (java.lang.Integer.compareUnsigned(l, r) > 0).toInt()
    fun Op_i32_le_u(l: Int, r: Int): Int = (java.lang.Integer.compareUnsigned(l, r) <= 0).toInt()
    fun Op_i32_ge_u(l: Int, r: Int): Int = (java.lang.Integer.compareUnsigned(l, r) >= 0).toInt()
    fun Op_i32_lt_s(l: Int, r: Int): Int = (l < r).toInt()
    fun Op_i32_le_s(l: Int, r: Int): Int = (l <= r).toInt()
    fun Op_i32_gt_s(l: Int, r: Int): Int = (l > r).toInt()
    fun Op_i32_ge_s(l: Int, r: Int): Int = (l >= r).toInt()

    fun Op_i64_eqz(l: Long): Int = (l == 0L).toInt()
    fun Op_i64_eq(l: Long, r: Long): Int = (l == r).toInt()
    fun Op_i64_ne(l: Long, r: Long): Int = (l != r).toInt()
    fun Op_i64_lt_u(l: Long, r: Long): Int = (java.lang.Long.compareUnsigned(l, r) < 0).toInt()
    fun Op_i64_gt_u(l: Long, r: Long): Int = (java.lang.Long.compareUnsigned(l, r) > 0).toInt()
    fun Op_i64_le_u(l: Long, r: Long): Int = (java.lang.Long.compareUnsigned(l, r) <= 0).toInt()
    fun Op_i64_ge_u(l: Long, r: Long): Int = (java.lang.Long.compareUnsigned(l, r) >= 0).toInt()
    fun Op_i64_lt_s(l: Long, r: Long): Int = (l < r).toInt()
    fun Op_i64_le_s(l: Long, r: Long): Int = (l <= r).toInt()
    fun Op_i64_gt_s(l: Long, r: Long): Int = (l > r).toInt()
    fun Op_i64_ge_s(l: Long, r: Long): Int = (l >= r).toInt()

    fun Op_f64_eq(l: Double, r: Double): Int = (l == r).toInt()
    fun Op_f64_ne(l: Double, r: Double): Int = (l != r).toInt()
    fun Op_f64_lt(l: Double, r: Double): Int = (l < r).toInt()
    fun Op_f64_le(l: Double, r: Double): Int = (l <= r).toInt()
    fun Op_f64_gt(l: Double, r: Double): Int = (l > r).toInt()
    fun Op_f64_ge(l: Double, r: Double): Int = (l >= r).toInt()

    fun checkAddress(address: Int, offset: Int): Int {
        val raddress = address + offset
        if (raddress !in 0 .. heap.limit() - 4) {
            println("ADDRESS: $raddress ($address + $offset)")
        }
        //println("ADDRESS: $raddress ($address + $offset)")
        return raddress
    }

    fun Op_f32_load(address: Int, offset: Int, align: Int): Float = heap.getFloat(checkAddress(address, offset))
    fun Op_f64_load(address: Int, offset: Int, align: Int): Double = heap.getDouble(checkAddress(address, offset))
    fun Op_i64_load(address: Int, offset: Int, align: Int): Long = heap.getLong(checkAddress(address, offset))
    fun Op_i32_load(address: Int, offset: Int, align: Int): Int = heap.getInt(checkAddress(address, offset))
    fun Op_i32_load8_s(address: Int, offset: Int, align: Int): Int {
        val raddr = checkAddress(address, offset)
        val value = heap.get(raddr).toInt()
        //println("GET HEAP8[$raddr] == $value")
        return value
    }

    fun Op_i32_load8_u(address: Int, offset: Int, align: Int): Int = Op_i32_load8_s(address, offset, align) and 0xFF

    fun Op_f32_store(address: Int, offset: Int, align: Int, value: Float): Unit =
        run { heap.putFloat(checkAddress(address, offset), value) }

    fun Op_f64_store(address: Int, offset: Int, align: Int, value: Double): Unit =
        run { heap.putDouble(checkAddress(address, offset), value) }

    fun Op_i64_store(address: Int, offset: Int, align: Int, value: Long): Unit =
        run { heap.putLong(checkAddress(address, offset), value) }

    fun Op_i32_store(address: Int, offset: Int, align: Int, value: Int): Unit =
        run { heap.putInt(checkAddress(address, offset), value) }

    fun Op_i32_store8(address: Int, offset: Int, align: Int, value: Int): Unit =
        run { heap.put(checkAddress(address, offset), value.toByte()) }

    fun Op_i32_store16(address: Int, offset: Int, align: Int, value: Int): Unit =
        run { heap.putShort(checkAddress(address, offset), value.toShort()) }

    fun Op_i64_store8(address: Int, offset: Int, align: Int, value: Long): Unit =
        Op_i32_store8(address, offset, align, value.toInt())

    fun Op_i64_store16(address: Int, offset: Int, align: Int, value: Long): Unit =
        Op_i32_store16(address, offset, align, value.toInt())

    fun Op_i64_store32(address: Int, offset: Int, align: Int, value: Long): Unit =
        Op_i32_store(address, offset, align, value.toInt())

    fun lw(address: Int) = Op_i32_load(address, 0, 2)
    fun lb(address: Int) = Op_i32_load8_s(address, 0, 0)
    fun lbu(address: Int) = Op_i32_load8_u(address, 0, 0)

    fun ___syscall(syscall: Int, address: Int): Int {
        //println("syscall($syscall, $address)")
        //TODO()
        // SYS_writev: 146,
        // SYS_close: 6,
        // SYS__llseek: 140,
        // SYS_ioctl: 54,
        when (syscall) {
            54 -> {
                val fd = lw(address + 0)
                val param = lw(address + 4)
                //println("ioctl $fd, $param")
                return 0
            }
            146 -> {
                val stream = lw(address + 0)
                val iov = lw(address + 4)
                val iovcnt = lw(address + 8)
                //println("SYS_writev $stream, $iov, $iovcnt")
                var ret = 0
                for (cc in 0 until iovcnt) {
                    val ptr = lw((iov + (cc * 8)) + 0)
                    val len = lw((iov + (cc * 8)) + 4)
                    //println("chunk: $ptr, $len")
                    for (n in 0 until len) {
                        printChar(stream, lbu(ptr + n))
                    }
                    ret += len
                }
                return ret
                /*
                for (n in 0 until len) {
                    printChar(lbu(ptr + n))
                    //println("WRITEV[$n]: ${lb(ptr + n)}")
                }
                return len
                */
            }
        }
        TODO("syscall $syscall")
    }

    fun printChar(stream: Int, c: Int) {
        when (stream) {
            1 -> System.out.print(c.toChar())
            else -> System.err.print(c.toChar())
        }
        //println("$stream: $c")
    }

    fun _emscripten_memcpy_big(a: Int, b: Int, c: Int): Int {
        TODO()
    }

    fun enlargeMemory(): Int {
        TODO()
        return 0
    }

    fun __putBytes(address: Int, data: ByteArray) {
        for (n in 0 until data.size) heap.put(address + n, data[n])
    }

    fun abort(value: Int): Unit {
        throw RuntimeException("ABORT $value")
    }

    fun abortStackOverflow(count: Int) {
        throw RuntimeException("abortStackOverflow($count)")
    }

    fun abortOnCannotGrowMemory() {
        throw RuntimeException("abortOnCannotGrowMemory")
    }

    fun getTotalMemory(): Int {
        return heap.limit()
    }

    fun ___lock(addr: Int) {
    }

    fun ___unlock(addr: Int) {
    }

    fun ___setErrNo(errno: Int) {

    }

    fun nullFunc_ii(v: Int): Int {
        return 0
    }

    fun nullFunc_iiii(v: Int): Int {
        return 0
    }

    fun putBytes(address: Int, bytes: ByteArray, offset: Int = 0, size: Int = bytes.size) {
        heap.position(address)
        heap.put(bytes, offset, size)
    }

    fun getBytes(address: Int, size: Int): ByteArray {
        val out = ByteArray(size)
        heap.position(address)
        heap.get(out)
        return out
    }

    fun putString(address: Int, string: String, charset: Charset) {
        putBytes(address, string.toByteArray(charset))
    }

    fun getBytez(address: Int, max: Int = Int.MAX_VALUE): ByteArray {
        val builder = ByteArrayBuilder()
        for (n in 0 until max) {
            val v = lbu(address + n)
            if (v == 0) break
            builder.append(v.toByte())
        }
        return builder.toByteArray()
    }

    fun getStringz(address: Int, charset: Charset): String {
        return getBytez(address).toString(charset)
    }
}