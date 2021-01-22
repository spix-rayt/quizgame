package io.spixy.quizgame

import com.google.gson.JsonObject
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

fun processMessage(jsonObject: JsonObject, outgoing: SendChannel<Frame>) {
    val messageType = jsonObject.get("type")?.asString ?: throw error("message without type")
    if(messageType == "auth") {
        val name = jsonObject.get("name").asString
        val key = jsonObject.get("key").asString
        val permissions = codes[key]
        if(game.gamePhase != GamePhase.SPLASHSCREEN &&
            permissions == PlayerPermissions.PLAYER &&
            !game.players.containsKey(name)) {
            return
        }
        if(permissions != null) {
            val player = game.players[name] ?: Player(permissions)
            player.name = name
            player.outgoing = outgoing
            game.players[player.name] = player
            player.sendMessage(mapOf(
                "type" to "auth",
                "authorized" to true,
                "permissions" to permissions.name,
                "playerName" to player.name
            ))
            game.updatePlayersForAll()
            player.sendMessage(game.basicGameStateToJson(player.permissions))
            game.players.values.forEach {
                player.sendMessage(it.toUpdatePlayerAvatarMessage())
            }
        } else {
            GlobalScope.launch(Dispatchers.IO) {
                outgoing.send(mapOf(
                    "type" to "auth",
                    "authorized" to false
                ))
            }
        }
    }
    val player = game.players.values.firstOrNull { it.outgoing == outgoing } ?: return

    if(player.permissions == PlayerPermissions.SPECTATOR) {
        return
    }

    if(player.permissions == PlayerPermissions.PLAYER) {
        if(messageType == "uploadAvatar") {
            player.avatar = jsonObject.get("image").asString
            val updatePlayerAvatar = player.toUpdatePlayerAvatarMessage()
            game.players.values.forEach {
                it.sendMessage(updatePlayerAvatar)
            }
        }
        if(messageType == "spacePressed") {
            if(game.gamePhase == GamePhase.QUESTION && game.players.values.none { it.shouldSelectedByAdmin }) {
                if(!player.answerBlock) {
                    player.readyToAnswer = true
                    if(game.getReadyToAnswerPlayersCount() == 1) {
                        game.playerReadyToAnswerStartTime = System.currentTimeMillis()
                    }
                    game.updatePlayersForAll()
                }
            }
        }
    }

    if(player.permissions == PlayerPermissions.ADMIN) {
        if(messageType == "startGame") {
            game.startGame()
        }
        if(messageType == "questionOpen") {
            game.selectQuestion(
                jsonObject.get("category").asInt,
                jsonObject.get("question").asInt
            )
        }
        if(messageType == "spacePressed") {
            if(game.gamePhase == GamePhase.QUESTION) {
                game.allowToAnswer(10000L)
            }
        }
        if(messageType == "playerDoAnswer") {
            if(game.gamePhase == GamePhase.QUESTION) {
                game.playerDoAnswer(jsonObject.get("right").asBoolean)
            }
        }
        if(messageType == "testQuestion") {
            if(game.gamePhase == GamePhase.QUESTIONSTABLE) {
                game.generateAndOpenTestQuestion()
            }
        }
        if(messageType == "selectPlayer") {
            game.selectPlayer(jsonObject["player"].asString)
        }
    }
}