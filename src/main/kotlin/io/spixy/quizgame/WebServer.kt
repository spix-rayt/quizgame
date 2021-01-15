package io.spixy.quizgame

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.routing.get
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import java.io.File
import java.net.http.WebSocket

object WebServer {
    fun blockingStart() {
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
                                outgoing.send(Frame.Text("you sent: $text"))
                            }
                        }
                    }
                }
            }
        }.start(wait = true)
    }
}