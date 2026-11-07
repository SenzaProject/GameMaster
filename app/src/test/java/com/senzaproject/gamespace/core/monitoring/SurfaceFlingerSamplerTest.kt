package com.senzaproject.gamespace.core.monitoring

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceFlingerSamplerTest {
    private val game = "com.example.game"
    private val layerName = "SurfaceView[com.example.game/com.example.game.Main](BLAST)#7"

    private class FakeShell(private val respond: (List<String>) -> ShellResult?) : ShellRunner {
        val calls = mutableListOf<List<String>>()
        override suspend fun run(command: List<String>, timeoutMs: Long): ShellResult? {
            calls += command
            return respond(command)
        }
    }

    private fun ok(text: String) = ShellResult(0, text, "")

    private fun latency(base: Long, frames: Int, stepNs: Long = 33_333_333L): String = buildString {
        appendLine("16666666")
        for (i in 0 until frames) {
            val t = base + i * stepNs
            appendLine("$t\t$t\t$t")
        }
    }

    private fun shellWith(layers: String, latencyText: () -> String) = FakeShell { command ->
        if ("--list" in command) ok(layers) else ok(latencyText())
    }

    @Test fun pickLayer_prefersSurfaceViewAndSkipsBackgroundLayers() {
        val layers = listOf(
            "com.example.game/com.example.game.Main#0",
            "Background for SurfaceView[com.example.game/com.example.game.Main]#3",
            layerName,
            "com.other.app/com.other.app.Main#1",
        )
        assertEquals(layerName, pickLayer(layers, game))
    }

    @Test fun pickLayer_fallsBackToWindowLayerAndReturnsNullWhenAbsent() {
        assertEquals(
            "com.example.game/com.example.game.Main#0",
            pickLayer(listOf("com.example.game/com.example.game.Main#0"), game),
        )
        assertNull(pickLayer(listOf("com.other.app/com.other.app.Main#1"), game))
    }

    @Test fun sample_returnsFramesFromSurfaceFlingerOutput() = runBlocking {
        val shell = shellWith(layerName) { latency(1_000_000_000L, 31) }
        val reading = SurfaceFlingerSampler(shell).sample(game)
        assertTrue(reading is SfReading.Frames)
        assertEquals(30f, (reading as SfReading.Frames).sample.fps, 0.01f)
    }

    @Test fun sample_reportsNoNewFramesWhenNothingWasPresented() = runBlocking {
        val sampler = SurfaceFlingerSampler(shellWith(layerName) { latency(1_000_000_000L, 31) })
        sampler.sample(game)
        assertEquals(SfReading.NoNewFrames, sampler.sample(game))
    }

    @Test fun sample_looksUpTheLayerOnlyOnce() = runBlocking {
        val shell = shellWith(layerName) { latency(1_000_000_000L, 31) }
        val sampler = SurfaceFlingerSampler(shell)
        sampler.sample(game)
        assertEquals(2, shell.calls.size)
        sampler.sample(game)
        assertEquals(3, shell.calls.size)
    }

    @Test fun sample_reportsMissingDumpPermission() = runBlocking {
        val denied = "Permission Denial: can't dump SurfaceFlinger from pid=1, uid=10200 due to missing android.permission.DUMP permission"
        val reading = SurfaceFlingerSampler(FakeShell { ok(denied) }).sample(game)
        assertEquals(SfReading.Unavailable(SfFailure.PERMISSION_DENIED), reading)
    }

    @Test fun sample_reportsGameNotOnScreen() = runBlocking {
        val shell = shellWith("com.other.app/com.other.app.Main#1") { "" }
        assertEquals(SfReading.Unavailable(SfFailure.LAYER_NOT_FOUND), SurfaceFlingerSampler(shell).sample(game))
    }

    @Test fun sample_reportsCommandFailure() = runBlocking {
        val reading = SurfaceFlingerSampler(FakeShell { null }).sample(game)
        assertEquals(SfReading.Unavailable(SfFailure.COMMAND_FAILED), reading)
    }

    @Test fun sample_forgetsTheLayerWhenItReturnsNoData() = runBlocking {
        var latencyText = ""
        val shell = shellWith(layerName) { latencyText }
        val sampler = SurfaceFlingerSampler(shell)
        assertEquals(SfReading.Unavailable(SfFailure.NO_DATA), sampler.sample(game))
        val callsAfterFirst = shell.calls.size
        latencyText = latency(1_000_000_000L, 31)
        assertTrue(sampler.sample(game) is SfReading.Frames)
        // The second sample had to list layers again before asking for latency.
        assertEquals(callsAfterFirst + 2, shell.calls.size)
    }
}
