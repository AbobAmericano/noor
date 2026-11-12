package com.example.sensors

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DhikrHapticHelperTest {

    private class RecordingView(context: Context) : View(context) {
        val feedbackConstants = mutableListOf<Int>()

        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            feedbackConstants.add(feedbackConstant)
            return true
        }
    }

    @Test
    fun performCountFeedback_triggersDistinctPatternsForBeadSubMilestoneAndTarget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = DhikrHapticHelper(context)
        val view = RecordingView(context)

        helper.performCountFeedback(view, newCount = 1L, dailyTarget = 100L, enabled = true)
        helper.performCountFeedback(view, newCount = 33L, dailyTarget = 100L, enabled = true)
        helper.performCountFeedback(view, newCount = 100L, dailyTarget = 100L, enabled = true)
        helper.performSoftUndo(view, enabled = true)

        val expectedTargetConst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }

        assertEquals(
            listOf(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.LONG_PRESS,
                expectedTargetConst,
                HapticFeedbackConstants.CLOCK_TICK
            ),
            view.feedbackConstants
        )
    }

    @Test
    fun performCountFeedback_whenDisabled_emitsNoHapticFeedback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = DhikrHapticHelper(context)
        val view = RecordingView(context)

        helper.performCountFeedback(view, newCount = 1L, dailyTarget = 33L, enabled = false)
        helper.performCountFeedback(view, newCount = 33L, dailyTarget = 33L, enabled = false)
        helper.performSoftUndo(view, enabled = false)

        assertTrue(view.feedbackConstants.isEmpty())
    }
}
