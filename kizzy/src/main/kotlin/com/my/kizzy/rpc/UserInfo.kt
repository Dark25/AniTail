package com.my.kizzy.rpc

/**
 * Created by Zion Huang
 * Modified to include avatar
 */
data class UserInfo(
    val username: String,
    val name: String,
    val avatarUrl: String? = null,
)
