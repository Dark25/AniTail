package com.anitail.music.constants

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIBRARY = "TOGGLE_LIBRARY"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_CLOSE_PLAYER = "CLOSE_PLAYER"
    val CommandToggleLibrary = SessionCommand(ACTION_TOGGLE_LIBRARY, Bundle.EMPTY)
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandClosePlayer = SessionCommand(ACTION_CLOSE_PLAYER, Bundle.EMPTY)
}
