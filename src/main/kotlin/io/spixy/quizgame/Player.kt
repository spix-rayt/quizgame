package io.spixy.quizgame

import com.google.gson.JsonObject
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

enum class PlayerPermissions {
    ADMIN, PLAYER, SPECTATOR
}

class Player(val permissions: PlayerPermissions) {
    var name: String = ""
    var outgoing: SendChannel<Frame>? = null
    var points = 0
    var readyToAnswer = false
    var answerBlock = false
    var avatar = ""
    var shouldSelectedByAdmin = false

    fun sendMessage(payload: Map<String, Any?>) {
        GlobalScope.launch(Dispatchers.IO) {
            outgoing?.send(payload)
        }
    }

    fun sendUpdateAllPlayers() {
        sendMessage(mapOf(
            "type" to "updateState",
            "players" to game.getParticipatingPlayers().map { it.toJson() }
        ))
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("points", points)
            addProperty("online", outgoing != null)
            addProperty("readyToAnswer", readyToAnswer)
            addProperty("answers", game.playerAnswers == this@Player)
            addProperty("selectsNextQuestion", game.playerSelectsNextQuestion == this@Player)
            addProperty("shouldSelectedByAdmin", shouldSelectedByAdmin)
        }
    }

    fun toUpdatePlayerAvatarMessage(): Map<String, String> {
        return mapOf(
            "type" to "updatePlayerAvatar",
            "name" to name,
            "avatar" to avatar
        )
    }
}