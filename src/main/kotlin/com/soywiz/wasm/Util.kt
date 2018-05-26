package com.soywiz.wasm

fun <K, V> Iterable<Pair<K, V>>.toMapList(): Map<K, List<V>> {
    val out = LinkedHashMap<K, ArrayList<V>>()
    for ((k, v) in this) {
        val array = out.getOrPut(k) { arrayListOf() }
        array.add(v)
    }
    return out
}
