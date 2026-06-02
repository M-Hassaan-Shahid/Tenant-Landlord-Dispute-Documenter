package com.example.tenant_landlorddisputedocumenter.data.remote

import com.google.firebase.firestore.DocumentSnapshot

/** Reads string lists from Firestore whether stored as native arrays or legacy strings. */
fun DocumentSnapshot.stringList(field: String): List<String> {
    val raw = get(field) ?: return emptyList()
    return when (raw) {
        is List<*> -> raw.mapNotNull { element ->
            when (element) {
                is String -> element
                else -> element?.toString()
            }
        }
        is String -> if (raw.isEmpty()) emptyList() else raw.split('\u001F').filter { it.isNotEmpty() }
        else -> emptyList()
    }
}
