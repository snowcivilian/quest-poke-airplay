package io.github.snowcivilian.questairplay.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMathTest {
    @Test fun fullVolumeIsUnity() = assertEquals(1f, VolumeMath.appGain(100, false), 1e-6f)
    @Test fun zeroIsSilent() = assertEquals(0f, VolumeMath.appGain(0, false), 0f)
    @Test fun muteIsSilentAtAnyLevel() = assertEquals(0f, VolumeMath.appGain(80, true), 0f)
    @Test fun outOfRangeIsClamped() {
        assertEquals(1f, VolumeMath.appGain(150, false), 1e-6f)
        assertEquals(0f, VolumeMath.appGain(-5, false), 0f)
    }
    @Test fun halfwayIsMinus20dB() = assertEquals(0.1f, VolumeMath.appGain(50, false), 1e-4f)
    @Test fun gainRisesMonotonically() {
        val gains = (0..100).map { VolumeMath.appGain(it, false) }
        assertTrue(gains.zipWithNext().all { (a, b) -> b > a })
    }
    @Test fun ignoreSenderDropsSenderGain() =
        assertEquals(0.5f, VolumeMath.outputGain(0.5f, 0.1f, ignoreSender = true), 1e-6f)
    @Test fun senderGainMultipliesWhenNotIgnored() =
        assertEquals(0.05f, VolumeMath.outputGain(0.5f, 0.1f, ignoreSender = false), 1e-6f)
}
