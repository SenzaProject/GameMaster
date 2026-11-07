package com.senzaproject.gamespace.core.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTrackerTest {
    private val games = setOf("game.a", "game.b")
    private val isGame: (String) -> Boolean = { it in games }

    @Test fun startsWhenAGameComesToTheFront() {
        val tracker = SessionTracker()
        val events = tracker.onTick(1_000, "game.a", true, isGame)
        assertEquals(listOf(SessionEvent.Started("game.a", 1_000)), events)
        assertEquals("game.a", tracker.activePackage)
    }

    @Test fun ignoresAppsThatAreNotLibraryGames() {
        val tracker = SessionTracker()
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(1_000, "chat.app", true, isGame))
        assertNull(tracker.activePackage)
    }

    @Test fun noEventWhileTheGameStaysInFront() {
        val tracker = SessionTracker()
        tracker.onTick(0, "game.a", true, isGame)
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(2_000, "game.a", true, isGame))
    }

    @Test fun shortBreakDoesNotEndTheSession() {
        val tracker = SessionTracker(graceMs = 30_000)
        tracker.onTick(0, "game.a", true, isGame)
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(10_000, "home", true, isGame))
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(20_000, "home", true, isGame))
        // Back before the grace period ends: still the same session.
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(25_000, "game.a", true, isGame))
        assertEquals("game.a", tracker.activePackage)
        // The earlier absence must not count towards a later one.
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(60_000, "home", true, isGame))
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(80_000, "home", true, isGame))
    }

    @Test fun sessionEndsAfterGraceAndUsesTheTimeTheGameWasLeft() {
        val tracker = SessionTracker(graceMs = 30_000)
        tracker.onTick(0, "game.a", true, isGame)
        tracker.onTick(100_000, "game.a", true, isGame)
        tracker.onTick(102_000, "home", true, isGame)
        assertEquals(emptyList<SessionEvent>(), tracker.onTick(120_000, "home", true, isGame))
        val events = tracker.onTick(132_000, "home", true, isGame)
        assertEquals(listOf(SessionEvent.Ended("game.a", 0, 102_000)), events)
        assertNull(tracker.activePackage)
    }

    @Test fun screenOffCountsAsLeavingTheGame() {
        val tracker = SessionTracker(graceMs = 30_000)
        tracker.onTick(0, "game.a", true, isGame)
        tracker.onTick(5_000, "game.a", false, isGame)
        val events = tracker.onTick(40_000, "game.a", false, isGame)
        assertEquals(listOf(SessionEvent.Ended("game.a", 0, 5_000)), events)
    }

    @Test fun switchingToAnotherGameEndsTheFirstAtOnce() {
        val tracker = SessionTracker()
        tracker.onTick(0, "game.a", true, isGame)
        val events = tracker.onTick(50_000, "game.b", true, isGame)
        assertEquals(
            listOf(SessionEvent.Ended("game.a", 0, 50_000), SessionEvent.Started("game.b", 50_000)),
            events,
        )
        assertEquals("game.b", tracker.activePackage)
    }

    @Test fun finishEndsTheRunningSessionImmediately() {
        val tracker = SessionTracker()
        tracker.onTick(0, "game.a", true, isGame)
        assertEquals(SessionEvent.Ended("game.a", 0, 9_000), tracker.finish(9_000))
        assertNull(tracker.activePackage)
        assertNull(tracker.finish(10_000))
    }

    @Test fun finishDuringABreakEndsAtTheTimeTheGameWasLeft() {
        val tracker = SessionTracker()
        tracker.onTick(0, "game.a", true, isGame)
        tracker.onTick(4_000, "home", true, isGame)
        assertEquals(SessionEvent.Ended("game.a", 0, 4_000), tracker.finish(20_000))
    }
}

class ForegroundTrackerTest {
    @Test fun followsResumeAndPause() {
        val tracker = ForegroundTracker()
        tracker.onEvent(ForegroundTracker.RESUMED, "a")
        assertEquals("a", tracker.current)
        tracker.onEvent(ForegroundTracker.RESUMED, "b")
        assertEquals("b", tracker.current)
        tracker.onEvent(ForegroundTracker.PAUSED, "b")
        assertNull(tracker.current)
    }

    @Test fun aLatePauseOfAnOldAppDoesNotClearTheNewOne() {
        val tracker = ForegroundTracker()
        tracker.onEvent(ForegroundTracker.RESUMED, "a")
        tracker.onEvent(ForegroundTracker.RESUMED, "b")
        tracker.onEvent(ForegroundTracker.PAUSED, "a")
        tracker.onEvent(ForegroundTracker.STOPPED, "a")
        assertEquals("b", tracker.current)
    }

    @Test fun replayingEventsGivesTheSameResult() {
        val tracker = ForegroundTracker()
        repeat(2) {
            tracker.onEvent(ForegroundTracker.RESUMED, "a")
            tracker.onEvent(ForegroundTracker.PAUSED, "a")
            tracker.onEvent(ForegroundTracker.RESUMED, "b")
        }
        assertEquals("b", tracker.current)
    }

    @Test fun otherEventTypesAreIgnored() {
        val tracker = ForegroundTracker()
        tracker.onEvent(ForegroundTracker.RESUMED, "a")
        tracker.onEvent(16, "a") // screen non-interactive
        assertEquals("a", tracker.current)
    }

    @Test fun resetForgetsTheForegroundApp() {
        val tracker = ForegroundTracker()
        tracker.onEvent(ForegroundTracker.RESUMED, "a")
        tracker.reset()
        assertNull(tracker.current)
    }
}
