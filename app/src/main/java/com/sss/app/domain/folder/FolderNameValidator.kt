package com.sss.app.domain.folder

import com.sss.app.data.local.FolderEntity

sealed interface NameCheck {
    data class Ok(val name: String) : NameCheck
    data class Error(val message: String) : NameCheck
}

/** One validator for: Home "Add folder", Rename, and the bubble panel "New folder". */
object FolderNameValidator {
    const val MAX = 40
    private val reserved = setOf("other", "all screenshots")

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun check(raw: String, existing: List<FolderEntity>, ignoreId: Long? = null, parentId: Long? = null): NameCheck {
        val name = normalize(raw)
        if (name.isEmpty()) return NameCheck.Error("Enter a folder name")
        if (name.length > MAX) return NameCheck.Error("Max $MAX characters")
        if (name.lowercase() in reserved) return NameCheck.Error("\"$name\" is reserved")
        val clash = existing.any { it.id != ignoreId && it.parentId == parentId && it.name.equals(name, ignoreCase = true) }
        if (clash) return NameCheck.Error("A folder named '$name' already exists here")
        return NameCheck.Ok(name)
    }
}