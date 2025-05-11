package com.anitail.music.constants

import java.util.concurrent.TimeUnit

enum class BackupFrequency(val hours: Int) {
    ONE_HOUR(1),
    THREE_HOURS(3),
    SIX_HOURS(6),
    DAILY(24),
    WEEKLY(24 * 7);
    
    fun toMillis(): Long = TimeUnit.HOURS.toMillis(hours.toLong())
    
    fun getDisplayName(): String {
        return when (this) {
            ONE_HOUR -> "1 hour"
            THREE_HOURS -> "3 hours"
            SIX_HOURS -> "6 hours" 
            DAILY -> "Daily"
            WEEKLY -> "Weekly"
        }
    }
    
    companion object {
        fun fromHours(hours: Int): BackupFrequency {
            return when (hours) {
                1 -> ONE_HOUR
                3 -> THREE_HOURS
                6 -> SIX_HOURS
                24 -> DAILY
                24 * 7 -> WEEKLY
                else -> DAILY // Default to daily if unknown value
            }
        }
    }
}
