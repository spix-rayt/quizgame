package io.spixy.quizgame

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.Executors

val gson = GsonBuilder().serializeNulls().create()

val codes = mapOf(
    generateCode(16) to PlayerPermissions.ADMIN,
    generateCode(16) to PlayerPermissions.PLAYER,
    generateCode(16) to PlayerPermissions.SPECTATOR
)

val game = Game()

val gameThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

fun main() {
    codes.forEach { (k, v) ->
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
                val host = call.request.host()
                for (frame in incoming) {
                    when(frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            if(text.length < 1000) {
                                println(text)
                            }
                            GlobalScope.launch(gameThread) {
                                processMessage(gson.fromJson(text, JsonObject::class.java), outgoing, host == "localhost")
                            }
                        }
                    }
                }
            }
        }
    }.start(wait = true)
}

suspend fun SendChannel<Frame>.send(payload: Map<String, Any?>) {
    val json = gson.toJson(payload)
    if(json.length < 1000) {
        println("send $json")
    }
    this.send(Frame.Text(json))
}