package io.spixy.quizgame

import java.io.File

val roundList = mutableListOf<Round>()

class Round {
    val categoryList = mutableListOf<Category>()
}

class Category {
    var name: String = ""
    val questionsList = mutableListOf<Question>()
}

class Question {
    var text: String = ""
    var image: File? = null
    var answer: String = ""
}

class GameData(val folder: File) {
    init {
        var currentRound: Round? = null
        var currentCategory: Category? = null
        var currentQuestion: Question? = null
        File(folder, "main.txt").readLines().forEachIndexed { n, s ->
            val line = s.trimStart().toLowerCase()
            if(line.startsWith("#")) {
                return@forEachIndexed
            }

            if(line.startsWith("+раунд")) {
                currentRound = Round().also {
                    roundList.add(it)
                }
                currentCategory = null
                currentQuestion = null
            }

            if(line.startsWith("+категория")) {
                currentCategory = Category().also {
                    currentRound?.categoryList?.add(it)
                    it.name = line.substring("+категория".length).trim()
                }
                currentQuestion = null
            }

            if(line.startsWith("+вопрос")) {
                currentQuestion = Question().also {
                    currentCategory?.questionsList?.add(it)
                    it.text = line.substring("+вопрос".length).trim()
                }
            }

            if(line.contains("=")) {
                val (parameterName, value) = line.split("=", limit = 2).map { it.trim() }
                when(parameterName.toLowerCase()) {
                    "ответ" -> currentQuestion?.answer = value
                    "изображение" -> currentQuestion?.image = File(value)
                }
            }
        }
    }
}