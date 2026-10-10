package com.cd.statussaver

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class SaverAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigatesBetweenMainScreens() {
        compose.onNodeWithText(compose.activity.getString(R.string.statuses_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.tab_links)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.link_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.tab_saved)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.collection_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.tab_about)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.about_title)).assertIsDisplayed()
    }

    @Test fun receivesSharedLinkFromAndroidIntent() {
        val link = "https://example.com/shared-video.mp4"
        val launchIntent = Intent(compose.activity.intent)
        try {
            compose.runOnUiThread {
                compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
            compose.onNodeWithText(link).assertIsDisplayed()
        } finally {
            // ActivityScenario matches lifecycle events to its original launch intent.
            // onNewIntent legitimately changes the activity intent; restore the harness
            // identity after testing the share so teardown can track the activity.
            compose.runOnUiThread { compose.activity.intent = launchIntent }
        }
    }

    @Test fun keepsEnteredLinkWhenActivityIsRecreated() {
        val link = "https://example.com/entered-video.mp4"
        compose.onNodeWithText(compose.activity.getString(R.string.tab_links)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(link)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(link).assertIsDisplayed()
    }

    @Test fun showsAnErrorForAnInsecureLink() {
        compose.onNodeWithText(compose.activity.getString(R.string.tab_links)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("http://example.com/video.mp4")
        compose.onNodeWithText(compose.activity.getString(R.string.download)).performClick()
        compose.onNodeWithText("Enter an HTTPS link.").assertIsDisplayed()
    }
}
