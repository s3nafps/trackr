package com.trackr.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("username_set") val usernameSet: Boolean = false,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("invite_code") val inviteCode: String = "",
)
