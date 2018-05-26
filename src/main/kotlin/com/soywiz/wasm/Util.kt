package com.soywiz.wasm

import kotlin.math.*

fun <K, V> Iterable<Pair<K, V>>.toMapList(): Map<K, List<V>> {
    val out = LinkedHashMap<K, ArrayList<V>>()
    for ((k, v) in this) {
        val array = out.getOrPut(k) { arrayListOf() }
        array.add(v)
    }
    return out
}

fun ByteArray.chunks(chunkSize: Int): List<ByteArray> {
    val out = arrayListOf<ByteArray>()
    for (n in 0 until this.size step chunkSize) {
        out += this.sliceArray(n until min(n + chunkSize, this.size))
    }
    return out
}
