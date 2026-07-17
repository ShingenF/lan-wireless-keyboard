package com.local.virtualkeyboard.ui

internal data class TouchContact(
    val id: Int,
    val x: Float,
    val y: Float,
)

/** Tracks every contact from its own origin so opposing motion cannot look like a tap. */
internal class MultiTouchMovementTracker(
    private val touchSlop: Float,
) {
    private val origins = mutableMapOf<Int, TouchContact>()
    private var movementExceeded = false

    fun start(contacts: List<TouchContact>) {
        origins.clear()
        contacts.forEach { origins[it.id] = it }
        movementExceeded = contacts.size != 2
    }

    fun addContacts(contacts: List<TouchContact>): Boolean {
        contacts.forEach { origins.putIfAbsent(it.id, it) }
        if (origins.size != 2) movementExceeded = true
        return movementExceeded
    }

    fun update(contacts: List<TouchContact>): Boolean {
        if (movementExceeded) return true
        movementExceeded = contacts.any { contact ->
            val origin = origins[contact.id] ?: return@any true
            val dx = contact.x - origin.x
            val dy = contact.y - origin.y
            dx * dx + dy * dy > touchSlop * touchSlop
        }
        return movementExceeded
    }

    fun reset() {
        origins.clear()
        movementExceeded = false
    }
}
