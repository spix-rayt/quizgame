package io.spixy.quizgame

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class GamePhase {
    SPLASHSCREEN, QUESTIONSTABLE, QUESTION, ENDGAME
}

class Game {
    val players = mutableMapOf<String, Player>()
    val gameData = GameData(File("games/example"))
    val nextRounds = gameData.roundList.toMutableList()
    var currentRound = nextRounds.removeFirst()
    var gamePhase = GamePhase.SPLASHSCREEN
    var currentQuestion: Question = Question()
    var playerAnswers: Player? = null
    private var answersAllowedStartTime = -1L
    var playerReadyToAnswerStartTime = -1L

    fun setTimerForAllPlayers(milliseconds: Int) {
        val timerMessage = mapOf(
            "type" to "setTimer",
            "milliseconds" to milliseconds
        )
        players.values.forEach { it.sendMessage(timerMessage) }
    }

    fun allowToAnswer() {
        if(gamePhase == GamePhase.QUESTION) {
            answersAllowedStartTime = System.currentTimeMillis()
            setTimerForAllPlayers(10000)
            if(getReadyToAnswerPlayersCount() > 0) {
                playerReadyToAnswerStartTime = System.currentTimeMillis()
            }
        }
    }

    fun playerDoAnswer(right: Boolean) {
        if(gamePhase == GamePhase.QUESTION) {
            playerAnswers?.let { playerAnswers ->
                if(right) {
                    playerAnswers.points += currentQuestion.price
                    questionClose()
                } else {
                    playerAnswers.points -= currentQuestion.price
                    playerAnswers.readyToAnswer = false
                    playerAnswers.answerBlock = true
                    this.playerAnswers = null
                    updatePlayersForAll()
                    allowToAnswer()
                }
            }
        }
    }

    fun isAnswersAllowed(): Boolean {
        return answersAllowedStartTime != -1L
    }

    fun startGame() {
        if(gamePhase == GamePhase.SPLASHSCREEN) {
            gamePhase = GamePhase.QUESTIONSTABLE
            updateGameStateForAll()

            GlobalScope.launch(gameThread) {
                while (gamePhase != GamePhase.ENDGAME) {
                    if(isAnswersAllowed()) {
                        if(System.currentTimeMillis() - playerReadyToAnswerStartTime >= 600) {
                            val randomReadyToAnswerPlayer = game.getRandomReadyToAnswerPlayer()
                            if(randomReadyToAnswerPlayer != null) {
                                answersAllowedStartTime = -1L
                                setTimerForAllPlayers(0)
                                game.playerAnswers = randomReadyToAnswerPlayer
                                game.updatePlayersForAll()
                            }
                        }
                    }
                    if(isAnswersAllowed()) {
                        if(System.currentTimeMillis() - answersAllowedStartTime >= 10000) {
                            val randomReadyToAnswerPlayer = game.getRandomReadyToAnswerPlayer()
                            if(randomReadyToAnswerPlayer != null) {
                                answersAllowedStartTime = -1L
                                setTimerForAllPlayers(0)
                                game.playerAnswers = randomReadyToAnswerPlayer
                                game.updatePlayersForAll()
                            } else {
                                questionClose()
                            }
                        }
                    }
                    delay(10)
                }
            }
        }
    }

    fun updateGameStateForAll() {
        players.values.forEach { it.sendMessage(stateToJson(it.permissions)) }
    }

    fun updatePlayersForAll() {
        players.values.forEach { it.sendUpdateAllPlayers() }
    }

    fun questionOpen(category: Int, question: Int) {
        if(category in currentRound.categoryList.indices) {
            if(question in currentRound.categoryList[category].questionsList.indices) {
                val newQuestion = currentRound.categoryList[category].questionsList[question]
                if(newQuestion.enabled) {
                    currentQuestion = newQuestion
                    gamePhase = GamePhase.QUESTION
                    updateGameStateForAll()
                    playerAnswers = null
                    answersAllowedStartTime = -1L
                }
            }
        }
    }

    fun questionClose() {
        GlobalScope.launch(gameThread) {
            currentQuestion.enabled = false
            currentQuestion = Question().apply {
                text = currentQuestion.answer
            }
            players.values.forEach {
                it.readyToAnswer = false
                it.answerBlock = false
            }
            playerAnswers = null
            answersAllowedStartTime = -1L
            updateGameStateForAll()
            updatePlayersForAll()

            delay(2000)


            gamePhase = GamePhase.QUESTIONSTABLE
            if(countQuestions() == 0) {
                if(nextRounds.isNotEmpty()) {
                    currentRound = nextRounds.removeFirst()
                } else {
                    gamePhase = GamePhase.ENDGAME
                }
            }
            updateGameStateForAll()
            updatePlayersForAll()
        }
    }

    fun countQuestions(): Int {
        var result = 0
        currentRound.categoryList.forEach { category ->
            result += category.questionsList.count { it.enabled }
        }
        return result
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
        val categories = currentRound.categoryList.map { category ->
            JsonObject().apply {
                addProperty("name", category.name)
                add("questions", gson.toJsonTree(category.questionsList.map { question -> if(question.enabled) question.price else null }))
            }
        }
        return gson.toJsonTree(categories)
    }

    fun getReadyToAnswerPlayersCount(): Int {
        return players.values.count { it.readyToAnswer }
    }

    fun getRandomReadyToAnswerPlayer(): Player? {
        return players.values.filter { it.readyToAnswer }.randomOrNull()
    }
}