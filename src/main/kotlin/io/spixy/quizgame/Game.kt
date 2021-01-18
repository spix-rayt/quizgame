package io.spixy.quizgame

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.File

enum class GamePhase {
    SPLASHSCREEN, QUESTIONSTABLE, QUESTION, ENDGAME
}

class Game {
    val gameData = GameData(File("games/example"))
    var currentRound = 0
    var gamePhase = GamePhase.SPLASHSCREEN
    var currentQuestion: Question = Question()

    fun startGame() {
        currentRound = 0
        gamePhase = GamePhase.QUESTIONSTABLE
        players.values.forEach { it.sendMessage(stateToJson(it.permissions)) }
    }

    fun questionOpen(category: Int, question: Int) {
        val round = gameData.roundList[currentRound]
        if(category in round.categoryList.indices) {
            if(question in round.categoryList[category].questionsList.indices) {
                currentQuestion = round.categoryList[category].questionsList[question]
                gamePhase = GamePhase.QUESTION
                players.values.forEach { it.sendMessage(stateToJson(it.permissions)) }
            }
        }
    }

    fun stateToJson(permissions: PlayerPermissions): Map<String, Any?> {
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
                    "questionText" to currentQuestion.text,
                    "questionImage" to currentQuestion.image?.let { FileMapper.getCodeByFile(it) },
                    "questionAnswer" to (currentQuestion.answer.takeIf { permissions == PlayerPermissions.ADMIN } ?: "")
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