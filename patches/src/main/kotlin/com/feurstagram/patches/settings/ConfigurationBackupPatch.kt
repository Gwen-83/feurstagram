package com.feurstagram.patches.settings

import app.morphe.patcher.patch.resourcePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val PREFERENCES_FILE = "feurstagram_prefs"

private fun includePreferences(
    document: Document,
    parentName: String,
    requireClientSideEncryption: Boolean = false,
) {
    val parent = if (document.documentElement.tagName == parentName) {
        document.documentElement
    } else {
        document.documentElement.getElementsByTagName(parentName).item(0) as? Element
    } ?: error("Missing <$parentName> in ${document.documentElement.tagName}")

    for (index in 0 until parent.childNodes.length) {
        val child = parent.childNodes.item(index) as? Element ?: continue
        if (child.tagName == "include" &&
            child.getAttribute("domain") == "sharedpref" &&
            child.getAttribute("path") == PREFERENCES_FILE
        ) {
            return
        }
    }

    val include = document.createElement("include")
    include.setAttribute("domain", "sharedpref")
    include.setAttribute("path", PREFERENCES_FILE)
    if (requireClientSideEncryption) {
        include.setAttribute("requireFlags", "clientSideEncryption")
    }
    parent.appendChild(include)
}

@Suppress("unused")
val configurationBackupPatch = resourcePatch(
    name = "Feurstagram settings backup",
    description = "Includes only Feurstagram preferences in Android backups while preserving " +
        "Instagram's existing backup rules.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        document("res/xml/backup_rules.xml").use { backupRules ->
            includePreferences(backupRules, "full-backup-content", requireClientSideEncryption = true)
        }
        document("res/xml-v31/data_extraction_rules.xml").use { extractionRules ->
            includePreferences(extractionRules, "cloud-backup", requireClientSideEncryption = true)
            includePreferences(extractionRules, "device-transfer")
        }
    }
}
