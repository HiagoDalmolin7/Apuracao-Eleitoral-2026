package br.com.apuracaoeleitoral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectionMathTest {
    @Test fun extrapolatesProportionalVotes() {
        assertEquals(600_000.0, ElectionMath.projectVotes(120_000.0, 20.0), 0.001)
        assertEquals(450_000.0, ElectionMath.projectVotes(450_000.0, 100.0), 0.001)
    }

    @Test fun preservesShareInProportionalProjection() {
        val a = ElectionMath.projectVotes(1_000.0, 10.0)
        val b = ElectionMath.projectVotes(500.0, 10.0)
        assertEquals(2.0, a / b, 0.000001)
    }

    @Test fun refusesMissingOrInvalidPercentage() {
        for (p in listOf(-1.0, 0.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(0.0, ElectionMath.projectVotes(100.0, p), 0.0)
        }
        assertEquals(0.0, ElectionMath.projectVotes(-10.0, 50.0), 0.0)
    }

    private fun decision(
        a: Long = 650, b: Long = 350, done: Long = 80, total: Long = 100,
        electorate: Long = 1500, turnout: Long = 1000, location: String = "BR",
        valid: Long = 1000, ballots: Long = 1000
    ): Int? = ElectionMath.irreversibleWinner(
        location, a, b, done, total, electorate, turnout, valid, ballots
    )

    @Test fun noWinnerWhenOpponentCouldStillOvertake() {
        assertNull(decision()) // vantagem 300, até 500 votos pendentes
    }

    @Test fun winner22WhenLeadExceedsRemainingUpperBound() {
        assertEquals(22, decision(a = 800, b = 200, done = 90)) // 600 > 500
    }

    @Test fun winner13WhenLeadIsIrreversible() {
        assertEquals(13, decision(a = 200, b = 800, done = 90))
    }

    @Test fun strictBoundaryRequiresLeadGreaterThanRemaining() {
        assertNull(decision(a = 750, b = 250)) // 500 = 500: empate ainda possível
    }

    @Test fun fullyCountedAllowsWinnerWithAnyPositiveLead() {
        assertEquals(13, decision(a = 499, b = 501, done = 100))
    }

    @Test fun tieNeverProducesWinner() {
        assertNull(decision(a = 500, b = 500, done = 100))
    }

    @Test fun stateResultsDoNotElectPresident() {
        assertNull(decision(a = 800, b = 200, done = 100, location = "SP"))
    }

    @Test fun impossibleTurnoutIsRejected() {
        assertNull(decision(a = 800, b = 200, turnout = 900))
        assertNull(decision(a = 800, b = 200, electorate = 900))
    }

    @Test fun impossibleSectionProgressIsRejected() {
        assertNull(decision(done = 101, total = 100))
        assertNull(decision(done = 0, total = 100))
    }

    @Test fun mismatchWithOfficialTotalsIsRejected() {
        assertNull(decision(a = 800, b = 200, valid = 999))
        assertNull(decision(a = 800, b = 200, ballots = 999))
    }
}
