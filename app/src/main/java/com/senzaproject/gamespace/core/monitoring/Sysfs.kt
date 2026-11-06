package com.senzaproject.gamespace.core.monitoring

import java.io.File
import java.io.IOException

/**
 * Reads a small text file from /proc or /sys. Returns null when Android does not let this app read it,
 * which is common on newer versions. Callers must show null as "not available", never as a made-up value.
 */
internal fun readTextOrNull(path: String): String? = try {
    File(path).takeIf { it.canRead() }?.readText()
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}
