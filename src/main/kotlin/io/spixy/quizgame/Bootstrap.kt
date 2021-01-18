package io.spixy.quizgame

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

val gson = Gson()

val codes = mapOf(
    generateCode(16) to PlayerPermissions.ADMIN,
    generateCode(16) to PlayerPermissions.PLAYER,
    generateCode(16) to PlayerPermissions.SPECTATOR
)

val players = mutableMapOf<String, Player>()

val game = Game()

val gameThread = newSingleThreadContext("gameThread")

fun main() {
    codes.forEach { k, v ->
        println("$v $k")
    }

    embeddedServer(Netty, 8080) {
        install(WebSockets)
        routing {
            get ("/file/{code}") {
                call.parameters["code"]?.let { code ->
                    FileMapper.getFileByCode(code)?.let { file ->
                        call.respondFile(file)
                    }
                }
            }
            static("/") {
                files("client/build")
                default("client/build/index.html")
            }

            webSocket("/") {
                for (frame in incoming) {
                    when(frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            if(text.length < 1000) {
                                println(text)
                            }
                            GlobalScope.launch(gameThread) {
                                processMessage(gson.fromJson(text, JsonObject::class.java), outgoing)
                            }
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}

suspend fun processMessage(jsonObject: JsonObject, outgoing: SendChannel<Frame>) {
    val messageType = jsonObject.get("type")?.asString ?: throw error("message without type")
    if(messageType == "auth") {
        val name = jsonObject.get("name").asString
        val key = jsonObject.get("key").asString
        val permissions = codes[key]
        if(permissions == PlayerPermissions.PLAYER && game.gamePhase != GamePhase.SPLASHSCREEN) {
            return
        }
        if(permissions != null) {
            val player = players[name] ?: Player(permissions)
            player.name = name
            player.outgoing = outgoing
            players[player.name] = player
            outgoing.send(mapOf(
                "type" to "auth",
                "authorized" to true,
                "permissions" to permissions.name,
                "playerName" to player.name
            ))
            players.values.forEach { it.sendUpdateAllPlayers() }
            player.sendMessage(game.stateToJson(player.permissions))
            players.values.forEach {
                player.sendMessage(it.toUpdatePlayerAvatarMessage())
            }
        } else {
            outgoing.send(mapOf(
                "type" to "auth",
                "authorized" to false
            ))
        }
    }
    val player = players.values.firstOrNull { it.outgoing == outgoing } ?: return

    if(player.permissions == PlayerPermissions.SPECTATOR) {
        return
    }

    if(player.permissions == PlayerPermissions.PLAYER) {
        if(messageType == "uploadAvatar") {
            player.avatar = jsonObject.get("image").asString
            val updatePlayerAvatar = player.toUpdatePlayerAvatarMessage()
            players.values.forEach {
                it.sendMessage(updatePlayerAvatar)
            }
        }
        if(messageType == "spacePressed") {
            if(game.gamePhase == GamePhase.QUESTION) {
                player.readyToAnswer = true
                players.values.forEach { it.sendUpdateAllPlayers() }
            }
        }
    }

    if(player.permissions == PlayerPermissions.ADMIN) {
        if(messageType == "startGame") {
            game.startGame()
        }
        if(messageType == "questionOpen") {
            game.questionOpen(
                jsonObject.get("category").asInt,
                jsonObject.get("question").asInt
            )
        }
    }
}

suspend fun SendChannel<Frame>.send(payload: Map<String, Any?>) {
    val json = gson.toJson(payload)
    if(json.length < 1000) {
        println("send $json")
    }
    this.send(Frame.Text(json))
}