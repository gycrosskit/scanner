package io.github.gycrosskit.scanner.kuikly

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ScannerPreviewEventTest {
    @Test fun rawWhitespaceSurvivesObjectAndJsonEventsWhileMalformedDataIsIgnored() {
        val event = ScannerPreviewEvent()
        val values = mutableListOf<String>()
        event.onResult(values::add)
        val send = event.callbacks.getValue("onResult")
        for (value in listOf(" ", "\n", "raw")) {
            send(JSONObject().apply { put("value", value) })
            send(org.json.JSONObject().put("value", value).toString())
        }
        send(JSONObject().apply { put("value", "") })
        send(JSONObject().apply { put("value", 42) })
        send("not json")
        send(null)
        assertEquals(listOf(" ", " ", "\n", "\n", "raw", "raw"), values)
    }
}
