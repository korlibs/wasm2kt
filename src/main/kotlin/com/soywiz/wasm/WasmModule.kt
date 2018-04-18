import java.nio.*
import java.nio.charset.*

open class WasmModule @JvmOverloads constructor(
    val heapSize: Int = 16 * 1024 * 1024, // 16 MB
    val stackSize: Int = 32 * 1024 // 32 KB
) {
    val heap = ByteBuffer.allocate(heapSize).order(ByteOrder.nativeOrder())

    @JvmField var ABORT = -1
    @JvmField var DYNAMICTOP_PTR = 0
    @JvmField var STACK_INITIAL_SIZE = stackSize
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
        var bytes = ByteArray(1024)
        var bpos = 0
        for (n in 0 until max) {
            val v = lbu(address + n)
            if (v == 0) break
            if (bpos >= bytes.size - 1) {
                bytes = bytes.copyOf(bytes.size * 3)
            }
            bytes[bpos++] =v.toByte()
        }
        return bytes.copyOf(bpos)
    }

    fun getStringz(address: Int, charset: Charset): String {
        return getBytez(address).toString(charset)
    }
}

/*
var SYSCALL_NAME_TO_CODE = {
    SYS_restart_syscall: 0,
    SYS_exit: 1,
    SYS_fork: 2,
    SYS_read: 3,
    SYS_write: 4,
    SYS_open: 5,
    SYS_close: 6,
    SYS_waitpid: 7,
    SYS_creat: 8,
    SYS_link: 9,
    SYS_unlink: 10,
    SYS_execve: 11,
    SYS_chdir: 12,
    SYS_time: 13,
    SYS_mknod: 14,
    SYS_chmod: 15,
    SYS_lchown: 16,
    SYS_break: 17,
    SYS_oldstat: 18,
    SYS_lseek: 19,
    SYS_getpid: 20,
    SYS_mount: 21,
    SYS_umount: 22,
    SYS_setuid: 23,
    SYS_getuid: 24,
    SYS_stime: 25,
    SYS_ptrace: 26,
    SYS_alarm: 27,
    SYS_oldfstat: 28,
    SYS_pause: 29,
    SYS_utime: 30,
    SYS_stty: 31,
    SYS_gtty: 32,
    SYS_access: 33,
    SYS_nice: 34,
    SYS_ftime: 35,
    SYS_sync: 36,
    SYS_kill: 37,
    SYS_rename: 38,
    SYS_mkdir: 39,
    SYS_rmdir: 40,
    SYS_dup: 41,
    SYS_pipe: 42,
    SYS_times: 43,
    SYS_prof: 44,
    SYS_brk: 45,
    SYS_setgid: 46,
    SYS_getgid: 47,
    SYS_signal: 48,
    SYS_geteuid: 49,
    SYS_getegid: 50,
    SYS_acct: 51,
    SYS_umount2: 52,
    SYS_lock: 53,
    SYS_ioctl: 54,
    SYS_fcntl: 55,
    SYS_mpx: 56,
    SYS_setpgid: 57,
    SYS_ulimit: 58,
    SYS_oldolduname: 59,
    SYS_umask: 60,
    SYS_chroot: 61,
    SYS_ustat: 62,
    SYS_dup2: 63,
    SYS_getppid: 64,
    SYS_getpgrp: 65,
    SYS_setsid: 66,
    SYS_sigaction: 67,
    SYS_sgetmask: 68,
    SYS_ssetmask: 69,
    SYS_setreuid: 70,
    SYS_setregid: 71,
    SYS_sigsuspend: 72,
    SYS_sigpending: 73,
    SYS_sethostname: 74,
    SYS_setrlimit: 75,
    SYS_getrlimit: 76   /* Back compatible 2Gig limited rlimit */,
    SYS_getrusage: 77,
    SYS_gettimeofday: 78,
    SYS_settimeofday: 79,
    SYS_getgroups: 80,
    SYS_setgroups: 81,
    SYS_select: 82,
    SYS_symlink: 83,
    SYS_oldlstat: 84,
    SYS_readlink: 85,
    SYS_uselib: 86,
    SYS_swapon: 87,
    SYS_reboot: 88,
    SYS_readdir: 89,
    SYS_mmap: 90,
    SYS_munmap: 91,
    SYS_truncate: 92,
    SYS_ftruncate: 93,
    SYS_fchmod: 94,
    SYS_fchown: 95,
    SYS_getpriority: 96,
    SYS_setpriority: 97,
    SYS_profil: 98,
    SYS_statfs: 99,
    SYS_fstatfs: 100,
    SYS_ioperm: 101,
    SYS_socketcall: 102,
    SYS_syslog: 103,
    SYS_setitimer: 104,
    SYS_getitimer: 105,
    SYS_stat: 106,
    SYS_lstat: 107,
    SYS_fstat: 108,
    SYS_olduname: 109,
    SYS_iopl: 110,
    SYS_vhangup: 111,
    SYS_idle: 112,
    SYS_vm86old: 113,
    SYS_wait4: 114,
    SYS_swapoff: 115,
    SYS_sysinfo: 116,
    SYS_ipc: 117,
    SYS_fsync: 118,
    SYS_sigreturn: 119,
    SYS_clone: 120,
    SYS_setdomainname: 121,
    SYS_uname: 122,
    SYS_modify_ldt: 123,
    SYS_adjtimex: 124,
    SYS_mprotect: 125,
    SYS_sigprocmask: 126,
    SYS_create_module: 127,
    SYS_init_module: 128,
    SYS_delete_module: 129,
    SYS_get_kernel_syms: 130,
    SYS_quotactl: 131,
    SYS_getpgid: 132,
    SYS_fchdir: 133,
    SYS_bdflush: 134,
    SYS_sysfs: 135,
    SYS_personality: 136,
    SYS_afs_syscall: 137,
    SYS_setfsuid: 138,
    SYS_setfsgid: 139,
    SYS__llseek: 140,
    SYS_getdents: 141,
    SYS__newselect: 142,
    SYS_flock: 143,
    SYS_msync: 144,
    SYS_readv: 145,
    SYS_writev: 146,
    SYS_getsid: 147,
    SYS_fdatasync: 148,
    SYS__sysctl: 149,
    SYS_mlock: 150,
    SYS_munlock: 151,
    SYS_mlockall: 152,
    SYS_munlockall: 153,
    SYS_sched_setparam: 154,
    SYS_sched_getparam: 155,
    SYS_sched_setscheduler: 156,
    SYS_sched_getscheduler: 157,
    SYS_sched_yield: 158,
    SYS_sched_get_priority_max: 159,
    SYS_sched_get_priority_min: 160,
    SYS_sched_rr_get_interval: 161,
    SYS_nanosleep: 162,
    SYS_mremap: 163,
    SYS_setresuid: 164,
    SYS_getresuid: 165,
    SYS_vm86: 166,
    SYS_query_module: 167,
    SYS_poll: 168,
    SYS_nfsservctl: 169,
    SYS_setresgid: 170,
    SYS_getresgid: 171,
    SYS_prctl: 172,
    SYS_rt_sigreturn: 173,
    SYS_rt_sigaction: 174,
    SYS_rt_sigprocmask: 175,
    SYS_rt_sigpending: 176,
    SYS_rt_sigtimedwait: 177,
    SYS_rt_sigqueueinfo: 178,
    SYS_rt_sigsuspend: 179,
    SYS_pread64: 180,
    SYS_pwrite64: 181,
    SYS_chown: 182,
    SYS_getcwd: 183,
    SYS_capget: 184,
    SYS_capset: 185,
    SYS_sigaltstack: 186,
    SYS_sendfile: 187,
    SYS_getpmsg: 188,
    SYS_putpmsg: 189,
    SYS_vfork: 190,
    SYS_ugetrlimit: 191,
    SYS_mmap2: 192,
    SYS_truncate64: 193,
    SYS_ftruncate64: 194,
    SYS_stat64: 195,
    SYS_lstat64: 196,
    SYS_fstat64: 197,
    SYS_lchown32: 198,
    SYS_getuid32: 199,
    SYS_getgid32: 200,
    SYS_geteuid32: 201,
    SYS_getegid32: 202,
    SYS_setreuid32: 203,
    SYS_setregid32: 204,
    SYS_getgroups32: 205,
    SYS_setgroups32: 206,
    SYS_fchown32: 207,
    SYS_setresuid32: 208,
    SYS_getresuid32: 209,
    SYS_setresgid32: 210,
    SYS_getresgid32: 211,
    SYS_chown32: 212,
    SYS_setuid32: 213,
    SYS_setgid32: 214,
    SYS_setfsuid32: 215,
    SYS_setfsgid32: 216,
    SYS_pivot_root: 217,
    SYS_mincore: 218,
    SYS_madvise: 219,
    SYS_madvise1: 219,
    SYS_getdents64: 220,
    SYS_fcntl64: 221 /* 223 is unused */,
    SYS_gettid: 224,
    SYS_readahead: 225,
    SYS_setxattr: 226,
    SYS_lsetxattr: 227,
    SYS_fsetxattr: 228,
    SYS_getxattr: 229,
    SYS_lgetxattr: 230,
    SYS_fgetxattr: 231,
    SYS_listxattr: 232,
    SYS_llistxattr: 233,
    SYS_flistxattr: 234,
    SYS_removexattr: 235,
    SYS_lremovexattr: 236,
    SYS_fremovexattr: 237,
    SYS_tkill: 238,
    SYS_sendfile64: 239,
    SYS_futex: 240,
    SYS_sched_setaffinity: 241,
    SYS_sched_getaffinity: 242,
    SYS_set_thread_area: 243,
    SYS_get_thread_area: 244,
    SYS_io_setup: 245,
    SYS_io_destroy: 246,
    SYS_io_getevents: 247,
    SYS_io_submit: 248,
    SYS_io_cancel: 249,
    SYS_fadvise64: 250 /* 251 is available for reuse (was briefly sys_set_zone_reclaim) */,
    SYS_exit_group: 252,
    SYS_lookup_dcookie: 253,
    SYS_epoll_create: 254,
    SYS_epoll_ctl: 255,
    SYS_epoll_wait: 256,
    SYS_remap_file_pages: 257,
    SYS_set_tid_address: 258,
    SYS_timer_create: 259,
    SYS_timer_settime: 260,
    SYS_timer_gettime: 261,
    SYS_timer_getoverrun: 262,
    SYS_timer_delete: 263,
    SYS_clock_settime: 264,
    SYS_clock_gettime: 265,
    SYS_clock_getres: 266,
    SYS_clock_nanosleep: 267,
    SYS_statfs64: 268,
    SYS_fstatfs64: 269,
    SYS_tgkill: 270,
    SYS_utimes: 271,
    SYS_fadvise64_64: 272,
    SYS_vserver: 273,
    SYS_mbind: 274,
    SYS_get_mempolicy: 275,
    SYS_set_mempolicy: 276,
    SYS_mq_open : 277,
    SYS_mq_unlink: 278,
    SYS_mq_timedsend: 279,
    SYS_mq_timedreceive: 280,
    SYS_mq_notify: 281,
    SYS_mq_getsetattr: 282,
    SYS_kexec_load: 283,
    SYS_waitid: 284 /* SYS_sys_setaltroot: 285 */,
    SYS_add_key: 286,
    SYS_request_key: 287,
    SYS_keyctl: 288,
    SYS_ioprio_set: 289,
    SYS_ioprio_get: 290,
    SYS_inotify_init: 291,
    SYS_inotify_add_watch: 292,
    SYS_inotify_rm_watch: 293,
    SYS_migrate_pages: 294,
    SYS_openat: 295,
    SYS_mkdirat: 296,
    SYS_mknodat: 297,
    SYS_fchownat: 298,
    SYS_futimesat: 299,
    SYS_fstatat64: 300,
    SYS_unlinkat: 301,
    SYS_renameat: 302,
    SYS_linkat: 303,
    SYS_symlinkat: 304,
    SYS_readlinkat: 305,
    SYS_fchmodat: 306,
    SYS_faccessat: 307,
    SYS_pselect6: 308,
    SYS_ppoll: 309,
    SYS_unshare: 310,
    SYS_set_robust_list: 311,
    SYS_get_robust_list: 312,
    SYS_splice: 313,
    SYS_sync_file_range: 314,
    SYS_tee: 315,
    SYS_vmsplice: 316,
    SYS_move_pages: 317,
    SYS_getcpu: 318,
    SYS_epoll_pwait: 319,
    SYS_utimensat: 320,
    SYS_signalfd: 321,
    SYS_timerfd_create: 322,
    SYS_eventfd: 323,
    SYS_fallocate: 324,
    SYS_timerfd_settime: 325,
    SYS_timerfd_gettime: 326,
    SYS_signalfd4: 327,
    SYS_eventfd2: 328,
    SYS_epoll_create1: 329,
    SYS_dup3: 330,
    SYS_pipe2: 331,
    SYS_inotify_init1: 332,
    SYS_preadv: 333,
    SYS_pwritev: 334,
    SYS_recvmmsg: 337,
    SYS_prlimit64: 340,
    SYS_name_to_handle_at: 341,
    SYS_open_by_handle_at: 342,
    SYS_clock_adjtime: 343,
    SYS_syncfs: 344,
    SYS_sendmmsg: 345,
    SYS_setns: 346,
    SYS_process_vm_readv: 347,
    SYS_process_vm_writev: 348,
    SYS_kcmp: 349,
    SYS_finit_module: 350
  };
 */
