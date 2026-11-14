package dev.swinganalyzer.core

import kotlin.test.Test
import kotlin.test.assertTrue

class SampleClipTest {

    @Test
    fun sampleClipMeetsAcceptanceChecks() {
        val failed = SampleClip.checks().filter { !it.ok }
        assertTrue(failed.isEmpty(), failed.joinToString("\n") { "${it.name}: ${it.detail}" })
    }

    @Test
    fun knowledgeFileLoadsAndInterpolates() {
        val k = Knowledge.loadBundled()
        val (lo, hi) = k.profileRange("address_bend", k.club("7i"))
        assertTrue(lo == 26.0 && hi == 42.0)
        val (dlo, _) = k.profileRange("address_bend", k.club("driver"))
        assertTrue(dlo == 20.0)
    }
}
