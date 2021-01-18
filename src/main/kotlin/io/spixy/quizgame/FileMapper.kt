package io.spixy.quizgame

import java.io.File


//Сопоставляет файл с кодом, при первом обращении. И даёт возможность получить файл связанный с кодом или наоборот.
object FileMapper {
    val files = mutableMapOf<String, File>()

    fun getCodeByFile(file: File): String {
        val existedEntry = files.filterValues { it.path == file.path }.entries.firstOrNull()
        if(existedEntry != null) {
            return existedEntry.key
        }

        var newCode = ""
        do {
            newCode = generateCode(16)
        } while (files.containsKey(newCode))
        files[newCode] = file
        return newCode
    }

    fun getFileByCode(code: String): File? {
        return files[code]
    }
}