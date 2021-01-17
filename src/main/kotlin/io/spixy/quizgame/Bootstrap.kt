package io.spixy.quizgame

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.round
import kotlin.random.Random

val gson = Gson()

val codes = mapOf(
    generateCode(16) to PlayerPermissions.ADMIN,
    generateCode(16) to PlayerPermissions.PLAYER,
    generateCode(16) to PlayerPermissions.SPECTATOR
)

enum class PlayerPermissions {
    ADMIN, PLAYER, SPECTATOR
}

class Player(val permissions: PlayerPermissions) {
    var name: String = ""
    var outgoing: SendChannel<Frame>? = null
    var points = 0

    fun sendMessage(payload: Map<String, Any>) {
        GlobalScope.launch(Dispatchers.IO) {
            outgoing?.send(payload)
        }
    }

    fun sendUpdateAllPlayers() {
        sendMessage(mapOf(
            "type" to "updateState",
            "players" to players.values.filter { it.permissions == PlayerPermissions.PLAYER }.map { it.toJson() }
        ))
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("points", points)
            addProperty("avatar", "jett-avatar.jpg")
            addProperty("online", outgoing != null)
        }
    }
}

enum class GamePhase {
    SPLASHSCREEN, QUESTIONSTABLE, QUESTION, ENDGAME
}

val players = mutableMapOf<String, Player>()

val game = Game()

class Game {
    val gameData = GameData(File("games/example"))
    var currentRound = 0
    var gamePhase = GamePhase.SPLASHSCREEN
    var currentQuestion: Question = Question()

    fun startGame() {
        currentRound = 0
        gamePhase = GamePhase.QUESTIONSTABLE
        val updateState = stateToJson()
        players.values.forEach { it.sendMessage(updateState) }
    }

    fun questionOpen(category: Int, question: Int) {
        val round = gameData.roundList[currentRound]
        if(category in round.categoryList.indices) {
            if(question in round.categoryList[category].questionsList.indices) {
                currentQuestion = round.categoryList[category].questionsList[question]
                gamePhase = GamePhase.QUESTION
                val updateState = stateToJson()
                players.values.forEach { it.sendMessage(updateState) }
            }
        }
    }

    fun stateToJson(): Map<String, Any> {
        return when(gamePhase) {
            GamePhase.SPLASHSCREEN -> {
                mapOf(
                    "type" to "updateState",
                    "gamePhase" to gamePhase.name
                )
            }
            GamePhase.QUESTIONSTABLE -> {
                mapOf(
                    "type" to "updateState",
                    "gamePhase" to gamePhase.name,
                    "categories" to currentCategoriesToJson()
                )
            }
            GamePhase.QUESTION -> {
                mapOf(
                    "type" to "updateState",
                    "gamePhase" to gamePhase.name,
                    "questionText" to currentQuestion.text
                )
            }
            GamePhase.ENDGAME -> {
                mapOf(
                    "type" to "updateState",
                    "gamePhase" to gamePhase.name,
                )
            }
        }
    }

    fun currentCategoriesToJson(): JsonElement {
        val round = gameData.roundList[currentRound]
        val categories = round.categoryList.map { category ->
            JsonObject().apply {
                addProperty("name", category.name)
                add("questions", gson.toJsonTree(category.questionsList.map { question -> question.price }))
            }
        }
        return gson.toJsonTree(categories)
    }
}

fun main() {

    codes.forEach { k, v ->
        println("$v $k")
    }

    embeddedServer(Netty, 8080) {
        install(WebSockets)
        routing {
            static("/") {
                files("client/build")
                default("client/build/index.html")
            }

            webSocket("/") {
                for (frame in incoming) {
                    when(frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            println(text)
                            processMessage(gson.fromJson(text, JsonObject::class.java), outgoing)
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
        if(permissions != null) {
            val player = players[name] ?: Player(permissions)
            player.name = name
            player.outgoing = outgoing
            players[player.name] = player
            outgoing.send(mapOf(
                "type" to "auth",
                "result" to true,
                "permissions" to permissions.name
            ))
            players.values.forEach { it.sendUpdateAllPlayers() }
            player.sendMessage(game.stateToJson())
        } else {
            outgoing.send(mapOf(
                "type" to "auth",
                "result" to false
            ))
        }
    }
    val player = players.values.firstOrNull { it.outgoing == outgoing } ?: return

    if(player.permissions == PlayerPermissions.SPECTATOR) {
        return
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

suspend fun SendChannel<Frame>.send(payload: Map<String, Any>) {
    val json = gson.toJson(payload)
    println("send $json")
    this.send(Frame.Text(json))
}

fun generateCode(size: Int): String {
    return (1..size).map {
        val r = Random.nextInt(0, 10 + 26)
        if(r < 10) {
            '0'.plus(r)
        } else {
            'a'.plus(r - 10)
        }
    }.toCharArray().concatToString()
}