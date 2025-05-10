package com.anitail.music.constants

import java.util.concurrent.TimeUnit

enum class BackupFrequency(val hours: Int) {
    THREE_HOURS(3),
    SIX_HOURS(6),
    DAILY(24),
    WEEKLY(24 * 7);
    
    fun toMillis(): Long = TimeUnit.HOURS.toMillis(hours.toLong())
    
    fun getDisplayName(): String {
        return when (this) {
            THREE_HOURS -> "3 hours"
            SIX_HOURS -> "6 hours" 
            DAILY -> "Daily"
            WEEKLY -> "Weekly"
        }
    }
    
    companion object {
        fun fromHours(hours: Int): BackupFrequency {
            return when (hours) {
                3 -> THREE_HOURS
                6 -> SIX_HOURS
                24 -> DAILY
                24 * 7 -> WEEKLY
                else -> DAILY // Default to daily if unknown value
            }
        }
    }
}
