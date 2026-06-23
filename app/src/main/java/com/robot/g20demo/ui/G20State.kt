package com.robot.g20demo.ui

/**
 * Represents the state of the G20 Remote Controller.
 * @param channels An array of 16 integers representing the RC channels (usually 1000-2000).
 */
data class G20State(
    val channels: IntArray = IntArray(16) { 1500 }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as G20State
        return channels.contentEquals(other.channels)
    }

    override fun hashCode(): Int {
        return channels.contentHashCode()
    }
}
