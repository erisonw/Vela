package com.vela.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VelaLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndAiImportNavigationIsReachable() {
        composeRule.onNodeWithText("日历").fetchSemanticsNode()
        composeRule.onNodeWithText("AI 日程").performClick()
        composeRule.onNodeWithText("输入...").fetchSemanticsNode()
    }
}
