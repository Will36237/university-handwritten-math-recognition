package vn.edu.fpt.hmerdemo.network

import org.junit.Assert.assertEquals
import org.junit.Test


class HmerModelTest {
    @Test
    fun apiValuesRemainStable() {
        assertEquals("tamer_a3", HmerModel.Tamer.apiValue)
        assertEquals("unimumer_lora", HmerModel.UniMumer.apiValue)
    }

    @Test
    fun timeoutValuesRemainStable() {
        assertEquals(30_000, HmerModel.Tamer.timeoutMs)
        assertEquals(60_000, HmerModel.UniMumer.timeoutMs)
    }
}
