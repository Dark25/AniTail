package com.anitail.music.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAuthResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null
    /*
    `access_token` and `refresh_token` are required for persistent login.
    the spotify servers will be triggered again if a new import needs to be made.
    */

)