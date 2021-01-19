package io.spixy.quizgame

import java.io.File

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
    var price: Int = 0
    var enabled = true
}

class GameData(val folder: File) {
    val roundList = mutableListOf<Round>()


    init {
        var currentRound: Round? = null
        var currentCategory: Category? = null
        var currentQuestion: Question? = null
        var roundNumber = 0
        File(folder, "main.txt").readLines().forEachIndexed { n, s ->
            val line = s.trimStart()
            if(line.startsWith("#")) {
                return@forEachIndexed
            }

            if(line.toLowerCase().startsWith("+раунд")) {
                currentRound = Round().also {
                    roundList.add(it)
                }
                roundNumber++
                currentCategory = null
                currentQuestion = null
            }

            if(line.toLowerCase().startsWith("+категория")) {
                currentCategory = Category().also {
                    currentRound?.categoryList?.add(it)
                    it.name = line.substring("+категория".length).trim()
                }
                currentQuestion = null
            }

            if(line.toLowerCase().startsWith("+вопрос")) {
                currentQuestion = Question().also {
                    currentCategory?.let { currentCategory ->
                        currentCategory.questionsList.add(it)
                        it.text = line.substring("+вопрос".length).trim()
                        it.price = roundNumber * currentCategory.questionsList.size * 100
                    }
                }
            }

            if(line.contains("=")) {
                val (parameterName, value) = line.split("=", limit = 2).map { it.trim() }
                when(parameterName.toLowerCase()) {
                    "ответ" -> currentQuestion?.answer = value
                    "изображение" -> currentQuestion?.image = File(folder, value)
                }
            }
        }
    }
}