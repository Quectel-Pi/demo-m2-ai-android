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

class ExampleRecognitionTest {
    @Test
    fun tappingExamplesRecognizesTheMainSubject() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val examples = listOf(
                R.id.example_cat_card to listOf("cat", "tabby"),
                R.id.example_burger_card to listOf("burger"),
                R.id.example_dog_card to listOf("dog", "retriever"),
                R.id.example_mug_card to listOf("mug", "cup")
            )
            examples.forEach { (cardId, expectedLabels) ->
                val result = tapExampleAndWait(scenario, cardId)
                assertTrue(
                    "Example $cardId was classified as '$result', expected one of $expectedLabels.",
                    expectedLabels.any { result.contains(it, ignoreCase = true) }
                )
            }
        } finally {
            scenario.close()
        }
    }

    private fun tapExampleAndWait(scenario: ActivityScenario<MainActivity>, cardId: Int): String {
        var isLandscape = false
        scenario.onActivity { activity ->
            isLandscape =
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        val view = onView(withId(cardId))
        if (isLandscape) {
            view.perform(click())
        } else {
            view.perform(scrollTo(), click())
        }
        val deadline = SystemClock.elapsedRealtime() + TEST_TIMEOUT_MS
        var result = ""
        while (result.isBlank() && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val card = activity.findViewById<View>(R.id.result_card)
                if (card.visibility == View.VISIBLE) {
                    result = activity.findViewById<TextView>(R.id.result_name).text.toString()
                }
            }
            if (result.isBlank()) SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return result
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
