package io.github.rikka.animelog

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 在 JVM 上渲染界面截图，检查布局和初始数据。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class UiShotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) = rule.onRoot().captureRoboImage("${System.getProperty("shotDir")}/$name.png")

    @Test
    fun screens() {
        rule.waitForIdle()
        shot("1_list")
        rule.onNodeWithText("统计").performClick()
        rule.waitForIdle()
        shot("2_stats")
        rule.onNodeWithText("常看的制作公司（按作品计）").performScrollTo()
        shot("3_stats_more")
        rule.onNodeWithText("更多").performClick()
        rule.waitForIdle()
        shot("4_more")
        rule.onNodeWithText("检查匹配结果").performClick()
        rule.waitForIdle()
        shot("5_review")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("片单").performClick()
        rule.onAllNodesWithText("我的妹妹不可能这么可爱 第一季").onFirst().performClick()
        rule.waitForIdle()
        shot("6_detail")
    }
}
