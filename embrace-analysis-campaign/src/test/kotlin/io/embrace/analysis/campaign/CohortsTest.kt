package io.embrace.analysis.campaign

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cohort classifier against a threadtime `EmbVerify` capture shaped like a real one: a chunked
 * sdk-init span, unrelated spans and flush records, a second process that restored the session, a third
 * that created a new one after `pm clear`, and a trailing incomplete payload.
 */
class CohortsTest {

    private val log = """
        --------- beginning of main
        09-03 14:37:56.782 15291 15314 I EmbVerify: EMBV1 1 1/1 {"kind":"resource","attrs":{"android.os.api_level":"31"}}
        09-03 14:37:56.782 15291 15314 I EmbVerify: EMBV1 2 1/2 {"kind":"span","name":"emb-sdk-init","startNanos":1788471476551000000,"endNanos":1788471476573000000,"attrs":{"post-init-duration-ms":"5","start-first-session-duration-ms":"5",
        09-03 14:37:56.782 15291 15314 I EmbVerify: EMBV1 2 2/2 "emb.user_session_id":"DB14BE4F069363755BDB6A3CF11B7E3E","emb.app.version_startup_counter":"1","thread-name":"main","ended-in-foreground":"false","init-compile-filter":"verify"}}
        09-03 14:37:56.783 15291 15314 I EmbVerify: EMBV1 3 1/1 {"kind":"span","name":"emb-app-startup-cold","attrs":{"emb.user_session_id":"DB14BE4F069363755BDB6A3CF11B7E3E"}}
        09-03 14:37:56.783 15291 15314 I EmbVerify: EMBV1 9 1/1 {"kind":"flush","mode":"startup","count":7}
        09-03 14:38:59.100 15400 15420 I EmbVerify: EMBV1 2 1/1 {"kind":"span","name":"emb-sdk-init","startNanos":1788471539560000000,"endNanos":1788471539579000000,"attrs":{"post-init-duration-ms":"0","start-first-session-duration-ms":"0","emb.user_session_id":"DB14BE4F069363755BDB6A3CF11B7E3E","emb.app.version_startup_counter":"2","thread-name":"main","ended-in-foreground":"false"}}
        09-03 14:39:06.300 15500 15520 I EmbVerify: EMBV1 2 1/1 {"kind":"span","name":"emb-sdk-init","startNanos":1788471546329000000,"endNanos":1788471546350000000,"attrs":{"post-init-duration-ms":"3","start-first-session-duration-ms":"3","emb.user_session_id":"02F0678C8623C1153FEB55BBA890132F","emb.app.version_startup_counter":"1","thread-name":"main","ended-in-foreground":"false","init-compile-filter":"speed-profile"}}
        09-03 14:39:12.000 15600 15620 I EmbVerify: EMBV1 2 1/2 {"kind":"span","name":"emb-sdk-init","startNanos":1,"endNanos":2,"attrs":{"emb.user_session_id":"INCOMPLETE"
    """.trimIndent()

    @Test
    fun `chunks are reassembled, non-init records skipped, launches classified in order`() {
        val launches = Cohorts.classify(Cohorts.parseEmbVerify(log))
        assertEquals(3, launches.size)
        assertEquals(listOf(15291L, 15400L, 15500L), launches.map { it.pid })
        assertEquals(listOf("created", "restored", "created"), launches.map { it.cohort })
        assertEquals(listOf(0, 1, 2), launches.map { it.iteration })
        assertEquals("verify", launches[0].attrs["init-compile-filter"])
        assertNull(launches[1].attrs["init-compile-filter"])
        assertEquals(22.0, launches[0].initMs!!, 1e-9)
        assertEquals(
            "  iter 000  pid 15291  created  session DB14BE4F  counter 1  start-first-session 5 ms  post-init 5 ms  compile verify",
            Cohorts.launchLine(launches[0]),
        )
        assertEquals(
            "  iter 001  pid 15400  restored session DB14BE4F  counter 2  start-first-session 0 ms  post-init 0 ms  compile ?",
            Cohorts.launchLine(launches[1]),
        )
    }

    @Test
    fun `expected cohort follows the method and violations exempt a restoring arm's fresh first launch`() {
        assertEquals("created", Cohorts.expectedCohort("coldStartupBaselineProfileNewUserSession"))
        assertEquals("created", Cohorts.expectedCohort("coldStartupBaselineProfileExpiredUserSession"))
        assertEquals("restored", Cohorts.expectedCohort("coldStartupBaselineProfile"))
        assertNull(Cohorts.expectedCohort(null))

        val restoringArm = Cohorts.report(log, "coldStartupBaselineProfile")
        assertEquals(listOf(2), restoringArm.violations) // iteration 0 is the fresh install; iteration 2 re-created
        assertEquals(
            "cohorts: 3 launches, 2 created, 1 restored, 0 unknown; expected restored; violations: 1 (iterations 2)",
            restoringArm.summary,
        )
        val creatingArm = Cohorts.report(log, "coldStartupBaselineProfileNewUserSession")
        assertEquals(listOf(1), creatingArm.violations)
        assertEquals(
            "cohorts: 3 launches, 2 created, 1 restored, 0 unknown; expected created; violations: 1 (iterations 1)",
            creatingArm.summary,
        )
        val unknownArm = Cohorts.report(log, null)
        assertEquals(emptyList<Int>(), unknownArm.violations)
        assertEquals("cohorts: 3 launches, 2 created, 1 restored, 0 unknown; expected n/a; violations: 0", unknownArm.summary)

        val first = Cohorts.classify(Cohorts.parseEmbVerify(log.lines().filter { "15400" in it }.joinToString("\n")))
        assertEquals("unknown", first.single().cohort) // counter 2 with nothing before it

        val json = Cohorts.toJson(creatingArm, "coldStartupBaselineProfileNewUserSession")
        assertEquals("created", json.getValue("expected").jsonPrimitive.content)
        assertEquals(1, json.getValue("violations").jsonArray.size)
        assertEquals("restored", json.getValue("launches").jsonArray[1].jsonObject.getValue("cohort").jsonPrimitive.content)
    }
}
