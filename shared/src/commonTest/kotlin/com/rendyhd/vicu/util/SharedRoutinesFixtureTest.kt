package com.rendyhd.vicu.util

import com.rendyhd.vicu.util.RoutineArchiveFixture.expand
import com.rendyhd.vicu.util.RoutineArchiveFixture.fixture
import com.rendyhd.vicu.util.RoutineArchiveFixture.payload
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `writeKeep`, `sharedVisibility` and `loggedBy` vectors of
 * `test-fixtures/routine-archive-v1.json` (docs/cross-app-semantics-v1.md, sections 6.8 and 6.9).
 * The placeholder rule of section 6.3 is in the `merge` vectors ([RoutineMergeFixtureTest]).
 */
class SharedRoutinesFixtureTest {

    @Test
    fun `a write keeps the server's record of an occurrence already in that state`() {
        for (vector in fixture.writeKeep) {
            val local = RoutineEnvelope.Parsed(isCarrier = true, body = "", payload = payload(vector.local))
            val remote = RoutineEnvelope.Parsed(isCarrier = true, body = "", payload = payload(vector.remote))

            val sent = checkNotNull(RoutineEnvelope.mergeForWrite(local, remote)) { vector.name }

            assertEquals(
                vector.expect,
                sent.occurrences.values.sortedBy { it.scheduledDate }.associate { it.scheduledDate to it.modifiedBy },
                vector.name,
            )
        }
    }

    @Test
    fun `an account reads its own routines and the ones shared with it`() {
        for (vector in fixture.sharedVisibility) {
            val read = vector.carriers.filter { SharedRoutines.readsCarrier(it.createdBy, vector.me, it.shared) }
            assertEquals(vector.useCarriers, read.map { it.id }, vector.name)

            val routines = read.associate { it.routineId to it.shared }
            val parts = vector.parts.filter { SharedRoutines.countsPart(it.routineId, routines) }
            assertEquals(vector.useParts, parts.map { it.id }, vector.name)
        }
    }

    @Test
    fun `a person is named only for the record they logged`() {
        for (vector in fixture.loggedBy) {
            assertEquals(vector.expectUserId, expand(vector.record).loggedByUserId, vector.name)
        }
    }
}
