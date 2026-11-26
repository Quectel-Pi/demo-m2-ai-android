package com.quectel.m2aidemo

import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrExampleRecognitionTest {
    @Test
    fun tappingExamplesExtractsEnglishText() {
        val scenario = ActivityScenario.launch(TextRecognitionActivity::class.java)
        try {
            val examples = listOf(
                R.id.ocr_receipt_card to listOf("coffee", "latte"),
                R.id.ocr_menu_card to listOf("daily", "latte", "americano"),
                R.id.ocr_sign_card to listOf("library", "daily", "open")
            )
            examples.forEach { (cardId, expectedText) ->
                val recognizedText = tapExampleAndWait(scenario, cardId)
                assertTrue(
                    "Example $cardId did not include any of $expectedText: $recognizedText",
                    expectedText.any { recognizedText.contains(it, ignoreCase = true) }
                )
            }
        } finally {
            scenario.close()
        }
    }

    private fun tapExampleAndWait(
        scenario: ActivityScenario<TextRecognitionActivity>,
        cardId: Int
    ): String {
        var isLandscape = false
        scenario.onActivity { activity ->
            isLandscape =
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        val card = onView(withId(cardId))
        if (isLandscape) {
            card.perform(click())
        } else {
            card.perform(scrollTo(), click())
        }

        val deadline = SystemClock.elapsedRealtime() + TEST_TIMEOUT_MS
        var result = ""
        while (result.isBlank() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val resultCard = activity.findViewById<View>(R.id.ocr_result_card)
                if (resultCard.visibility == View.VISIBLE) {
                    result = activity.findViewById<TextView>(R.id.ocr_result_text).text.toString()
                }
            }
            if (result.isBlank()) SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return result
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
