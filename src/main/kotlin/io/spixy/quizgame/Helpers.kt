package io.spixy.quizgame

import kotlin.random.Random

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