package com.example.usbsorter

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.util.Random

object UsbOps {

    private val KEEP_EXT = setOf("mp3", "aac")

    /**
     * 1) mp3/aac -> в корень
     * 2) остальные файлы -> удалить
     * 3) пустые папки -> удалить
     * 4) файлы в корне -> переименовать в случайные 6-значные числа (расширение сохраняем)
     */
    fun process(context: Context, treeUri: Uri): String {
        val resolver = context.contentResolver
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return "Не удалось открыть дерево"

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)

        var moved = 0
        var deleted = 0
        var errors = 0

        // --- Фаза 1: обход, аудио → в корень, всё остальное → удалить ---
        fun walk(dir: DocumentFile) {
            for (child in dir.listFiles().filterNotNull()) {
                if (child.isDirectory) {
                    walk(child)
                    continue
                }
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in KEEP_EXT) {
                    try {
                        DocumentsContract.moveDocument(
                            resolver,
                            child.uri,
                            dir.uri,
                            rootDocUri
                        )
                        moved++
                    } catch (e: Exception) {
                        errors++
                    }
                } else {
                    try {
                        DocumentsContract.deleteDocument(resolver, child.uri)
                        deleted++
                    } catch (e: Exception) {
                        errors++
                    }
                }
            }
        }
        walk(root)

        // --- Фаза 2: рекурсивно удаляем пустые папки снизу вверх ---
        fun deleteEmptyDirs(dir: DocumentFile) {
            for (child in dir.listFiles().filterNotNull()) {
                if (child.isDirectory) {
                    deleteEmptyDirs(child)
                    if (child.listFiles().isNullOrEmpty()) {
                        runCatching { child.delete() }
                    }
                }
            }
        }
        deleteEmptyDirs(root)

        // --- Фаза 3: переименование аудио в корне в случайные цифры ---
        val rnd = Random(System.currentTimeMillis())
        val used = HashSet<String>()
        var renamed = 0

        for (file in root.listFiles().filterNotNull()) {
            if (file.isDirectory) continue
            val name = file.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in KEEP_EXT) continue

            var base: String
            do {
                base = (rnd.nextInt(900_000) + 100_000).toString()
            } while (!used.add(base))

            val newName = "$base.$ext"
            try {
                DocumentsContract.renameDocument(resolver, file.uri, newName)
                renamed++
            } catch (e: Exception) {
                errors++
            }
        }

        return buildString {
            appendLine("Готово.")
            appendLine("Перемещено в корень: $moved")
            appendLine("Удалено файлов: $deleted")
            appendLine("Переименовано: $renamed")
            appendLine("Ошибок: $errors")
            appendLine()
            appendLine("С любовью, от Серёжи ♥")
        }.trim()
    }
}
