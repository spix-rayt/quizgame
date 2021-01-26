package io.spixy.quizgame

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

enum class GamePhase {
    SPLASHSCREEN, QUESTIONSTABLE, QUESTION, ENDGAME
}

class Game {
    val players = mutableMapOf<String, Player>()
    val gameData = GameData(File("games/nebulpack"))
    val nextRounds = gameData.roundList.toMutableList()
    var currentRound = nextRounds.removeFirst()
    var gamePhase = GamePhase.SPLASHSCREEN
    var currentQuestion: Question = Question()
    var generatedQuestion: Question? = null
    var playerAnswers: Player? = null
    var playerSelectsNextQuestion: Player? = null
    private var answersAllowedStartTime = -1L
    private var timeToAnswer = 0L
    var playerReadyToAnswerStartTime = -1L

    fun setTimerForAllPlayers(milliseconds: Long) {
        val timerMessage = mapOf(
            "type" to "setTimer",
            "milliseconds" to milliseconds
        )
        players.values.forEach { it.sendMessage(timerMessage) }
    }

    fun allowToAnswer(time: Long) {
        if(gamePhase == GamePhase.QUESTION && players.values.none { it.shouldSelectedByAdmin }) {
            answersAllowedStartTime = System.currentTimeMillis()
            timeToAnswer = time
            setTimerForAllPlayers(time)
            if(getReadyToAnswerPlayersCount() > 0) {
                playerReadyToAnswerStartTime = System.currentTimeMillis()
            }
        }
    }

    fun playerDoAnswer(right: Boolean) {
        if(gamePhase == GamePhase.QUESTION) {
            playerAnswers?.let { playerAnswers ->
                if(right) {
                    playerAnswers.points += currentQuestion.realPrice ?: currentQuestion.price
                    playerSelectsNextQuestion = playerAnswers
                    closeQuestion()
                } else {
                    playerAnswers.points -= currentQuestion.realPrice ?: currentQuestion.price
                    playerAnswers.readyToAnswer = false
                    playerAnswers.answerBlock = true
                    this.playerAnswers = null
                    if(getParticipatingPlayers().all { it.answerBlock }) {
                        closeQuestion()
                    } else {
                        updatePlayersForAll()
                        allowToAnswer(4000L)
                    }
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
            playerSelectsNextQuestion = getParticipatingPlayers().randomOrNull()
            updatePlayersForAll()
            updateBasicGameStateForAll()

            GlobalScope.launch(gameThread) {
                while (gamePhase != GamePhase.ENDGAME) {
                    if(isAnswersAllowed()) {
                        if(System.currentTimeMillis() - playerReadyToAnswerStartTime >= 600) {
                            val randomReadyToAnswerPlayer = getRandomReadyToAnswerPlayer()
                            if(randomReadyToAnswerPlayer != null) {
                                setPlayerForAnswer(randomReadyToAnswerPlayer)
                            }
                        }
                    }
                    if(isAnswersAllowed()) {
                        if(System.currentTimeMillis() - answersAllowedStartTime >= timeToAnswer) {
                            val randomReadyToAnswerPlayer = getRandomReadyToAnswerPlayer()
                            if(randomReadyToAnswerPlayer != null) {
                                setPlayerForAnswer(randomReadyToAnswerPlayer)
                            } else {
                                closeQuestion()
                            }
                        }
                    }
                    delay(10)
                }
            }
        }
    }

    fun setPlayerForAnswer(player: Player) {
        answersAllowedStartTime = -1L
        setTimerForAllPlayers(0)
        playerAnswers = player
        players.values.forEach { it.readyToAnswer = false }
        updatePlayersForAll()
    }

    fun updateBasicGameStateForAll() {
        players.values.forEach { it.sendMessage(basicGameStateToJson(it.permissions)) }
    }

    fun updatePlayersForAll() {
        players.values.forEach { it.sendUpdateAllPlayers() }
    }

    fun generateAndOpenTestQuestion() {
        val question = Question()
        val a = Random.nextInt(1..9) * 10
        val b = Random.nextInt(1..9)
        question.text = "$a + $b ="
        question.answer = "${a + b}"
        question.price = 0
        question.isTestQuestion = true
        openQuestion(question)
    }

    fun selectQuestion(category: Int, question: Int) {
        if(category in currentRound.categoryList.indices) {
            if(question in currentRound.categoryList[category].questionsList.indices) {
                val newQuestion = currentRound.categoryList[category].questionsList[question]
                if(newQuestion.enabled) {
                    openQuestion(newQuestion)
                }
            }
        }
    }

    fun openQuestion(question: Question) {
        currentQuestion = question
        gamePhase = GamePhase.QUESTION
        playerAnswers = null
        answersAllowedStartTime = -1L

        if(currentQuestion.catTrap != null) {
            getParticipatingPlayers().filter { it != playerSelectsNextQuestion }.forEach { it.shouldSelectedByAdmin = true }
            updatePlayersForAll()
        }
        if(currentQuestion.video != null || currentQuestion.audio != null) {
            GlobalScope.launch(gameThread) {
                players.values.forEach { it.mediaReady = false }
                var waiting = true
                launch(gameThread) {
                    delay(10000)
                    waiting = false
                }

                while (waiting) {
                    delay(20)
                    if(players.values.all { it.mediaReady }) {
                        break
                    }
                }

                val playMediaMessage = mapOf(
                    "type" to "playMedia"
                )
                players.values.forEach { it.sendMessage(playMediaMessage) }
            }
        }

        updateBasicGameStateForAll()
    }

    fun closeQuestion() {
        GlobalScope.launch(gameThread) {
            sendAnswer()

            players.values.forEach {
                it.readyToAnswer = false
                it.answerBlock = false
            }
            playerAnswers = null
            answersAllowedStartTime = -1L
            currentQuestion.enabled = false
            gamePhase = GamePhase.QUESTIONSTABLE
            if(countQuestions() == 0) {
                nextRound()
            }
            generatedQuestion = null
            updateBasicGameStateForAll()
            updatePlayersForAll()
        }
    }

    fun sendAnswer() {
        val answerMessage = mapOf(
            "type" to "updateState",
            "answer" to mapOf(
                "text" to currentQuestion.answer,
                "video" to currentQuestion.videoAnswer?.let { FileMapper.getCodeByFile(it) }
            )
        )

        players.values.forEach { it.sendMessage(answerMessage) }
    }

    fun countQuestions(): Int {
        var result = 0
        currentRound.categoryList.forEach { category ->
            result += category.questionsList.count { it.enabled }
        }
        return result
    }

    fun basicGameStateToJson(permissions: PlayerPermissions): Map<String, Any?> {
        val sendQuestion = generatedQuestion ?: currentQuestion
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
                val result = hashMapOf<String, Any?>()
                result["type"] = "updateState"
                result["gamePhase"] = gamePhase.name
                result["questionText"] = sendQuestion.text
                result["questionImage"] = sendQuestion.image?.let { FileMapper.getCodeByFile(it) }
                result["questionAudio"] = sendQuestion.audio?.let { FileMapper.getCodeByFile(it) }
                result["questionAnswer"] = (sendQuestion.answer.takeIf { permissions == PlayerPermissions.ADMIN } ?: "")
                result["questionVideo"] = sendQuestion.video?.let { FileMapper.getCodeByFile(it) }

                if(sendQuestion.catTrap != null) {
                    result["questionText"] = sendQuestion.catTrap
                    result["questionImage"] = null
                    result["questionAudio"] = null
                    result["questionAnswer"] = null
                    result["questionCatTrap"] = sendQuestion.catTrap != null
                }
                result
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

    fun getParticipatingPlayers(): List<Player> {
        return players.values.filter { it.permissions == PlayerPermissions.PLAYER }
    }

    fun selectPlayer(playerName: String) {
        val player = players.values.firstOrNull { it.name == playerName }
        if(player != null && player.shouldSelectedByAdmin) {
            players.values.forEach { it.shouldSelectedByAdmin = false }

            if(currentQuestion.catTrap != null) {
                generatedQuestion = currentQuestion.generateSimplified()
                updateBasicGameStateForAll()
                getParticipatingPlayers().filter { it != player }.forEach { it.answerBlock = true }
                setPlayerForAnswer(player)
            }
        }
    }

    fun nextRound() {
        if(gamePhase == GamePhase.QUESTIONSTABLE) {
            if(nextRounds.isNotEmpty()) {
                currentRound = nextRounds.removeFirst()
            } else {
                gamePhase = GamePhase.ENDGAME
            }
            updateBasicGameStateForAll()
        }
    }
}