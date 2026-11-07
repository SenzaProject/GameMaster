package com.senzaproject.gamespace.core.monitoring

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

/**
 * Runs one command and captures its output. Behind an interface so tests can fake it and so a root-backed
 * runner can be added in phase 6 without touching the callers.
 */
interface ShellRunner {
    /** Returns null when the process could not be started or was killed by the timeout. */
    suspend fun run(command: List<String>, timeoutMs: Long = 3_000L): ShellResult?
}

/** Runs commands as this app's own user. It cannot do anything the app is not already allowed to do. */
@Singleton
class ProcessShellRunner @Inject constructor() : ShellRunner {
    override suspend fun run(command: List<String>, timeoutMs: Long): ShellResult? =
        withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(command).start()
                // Reading blocks until the process closes its output, so a watchdog enforces the timeout.
                val watchdog = launch {
                    delay(timeoutMs)
                    process.destroyForcibly()
                }
                try {
                    val out = process.inputStream.bufferedReader().readText()
                    val err = process.errorStream.bufferedReader().readText()
                    ShellResult(process.waitFor(), out, err)
                } finally {
                    watchdog.cancel()
                    process.destroy()
                }
            } catch (e: IOException) {
                null
            }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringModule {
    @Binds
    abstract fun shellRunner(impl: ProcessShellRunner): ShellRunner
}
