package com.nutritrack.app.utils

data class FastingTimerState(
    val isActive: Boolean = false,
    val protocol: FastingProtocol? = null,
    val elapsedMillis: Long = 0L,
    val targetMillis: Long = 0L,
    val progressPercent: Int = 0,
    val elapsedFormatted: String = "00:00:00",
    val remainingFormatted: String = "00:00:00",
    val phase: FastingPhase = FastingPhase.NOT_STARTED
)