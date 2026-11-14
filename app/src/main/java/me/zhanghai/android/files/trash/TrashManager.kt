/*
 * Notaria: the in-app trash.
 *
 * Deleted files are moved (renamed, so instantly) into a hidden folder on the same storage
 * volume, with a small sidecar file remembering where they came from, so they can be
 * restored. Only files on the primary shared storage are trashable; everything else
 * (SD card, network storage, archives) is still deleted permanently, after a confirmation.
 */

package me.zhanghai.android.files.trash

import android.os.Environment
import java8.nio.file.Path
import me.zhanghai.android.files.provider.linux.isLinuxPath
import org.json.JSONObject
import java.io.File

object TrashManager {
    const val RETENTION_DAYS = 30

    private val primaryRoot: File
        @Suppress("DEPRECATION")
        get() = Environment.getExternalStorageDirectory()

    val trashDir: File
        get() = File(primaryRoot, ".notaria_trash")

    private val infoDir: File
        get() = File(trashDir, ".info")

    fun isInTrash(file: File): Boolean =
        file.path == trashDir.path || file.path.startsWith(trashDir.path + "/")

    fun isInTrash(path: Path): Boolean = path.isLinuxPath && isInTrash(File(path.toString()))

    fun isTrashable(path: Path): Boolean {
        if (!path.isLinuxPath) {
            return false
        }
        val file = File(path.toString())
        return file.path.startsWith(primaryRoot.path + "/") && !isInTrash(file)
    }

    /** @return whether the file is now in the trash. */
    fun moveToTrash(path: Path): Boolean {
        if (!isTrashable(path)) {
            return false
        }
        val source = File(path.toString())
        trashDir.mkdirs()
        infoDir.mkdirs()
        val target = uniqueTarget(source.name)
        if (!source.renameTo(target)) {
            return false
        }
        val info = JSONObject()
            .put("originalPath", source.path)
            .put("deletedAt", System.currentTimeMillis())
        File(infoDir, target.name + ".json").writeText(info.toString())
        return true
    }

    /** @return the restored file, or null if it could not be restored. */
    fun restore(stored: File): File? {
        if (!isInTrash(stored) || !stored.exists()) {
            return null
        }
        val infoFile = File(infoDir, stored.name + ".json")
        val originalPath = runCatching { JSONObject(infoFile.readText()).getString("originalPath") }
            .getOrNull()
            ?: File(primaryRoot, "Restored/" + stored.name).path
        val original = File(originalPath)
        original.parentFile?.mkdirs()
        var target = original
        var index = 1
        while (target.exists()) {
            val base = original.nameWithoutExtension
            val extension = original.extension.let { if (it.isEmpty()) "" else ".$it" }
            target = File(original.parentFile, "$base (restored${if (index > 1) " $index" else ""})$extension")
            index++
        }
        if (!stored.renameTo(target)) {
            return null
        }
        infoFile.delete()
        return target
    }

    fun deleteForever(stored: File) {
        if (!isInTrash(stored)) {
            return
        }
        stored.deleteRecursively()
        File(infoDir, stored.name + ".json").delete()
    }

    fun emptyTrash() {
        trashDir.listFiles()?.forEach { if (it.name != ".info") it.deleteRecursively() }
        infoDir.deleteRecursively()
    }

    /** Permanently removes items that have been in the trash longer than [RETENTION_DAYS]. */
    fun purgeExpired() {
        val limit = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60 * 60 * 1000
        infoDir.listFiles()?.forEach { infoFile ->
            val deletedAt = runCatching { JSONObject(infoFile.readText()).getLong("deletedAt") }
                .getOrNull() ?: return@forEach
            if (deletedAt < limit) {
                deleteForever(File(trashDir, infoFile.name.removeSuffix(".json")))
            }
        }
    }

    fun sizeBytes(): Long =
        trashDir.takeIf { it.exists() }?.walkTopDown()
            ?.filter { it.isFile && !it.path.startsWith(infoDir.path) }
            ?.sumOf { it.length() } ?: 0L

    private fun uniqueTarget(name: String): File {
        var target = File(trashDir, name)
        var index = 1
        while (target.exists()) {
            val file = File(name)
            val extension = file.extension.let { if (it.isEmpty()) "" else ".$it" }
            target = File(trashDir, "${file.nameWithoutExtension} ($index)$extension")
            index++
        }
        return target
    }
}
