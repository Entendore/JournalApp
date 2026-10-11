package com.example.journalapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.RootMatchers.isDialog
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.journalapp.security.BiometricLock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalFlowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun calmState() {
        BiometricLock.disable(context)
        BiometricLock.State.shouldRelock = false
    }

    @Test
    fun addSearchAndDeleteAnEntry() {
        val needle = "Needle ${System.currentTimeMillis()}"
        val decoy = "Decoy ${System.currentTimeMillis()}"

        createEntry(needle)
        createEntry(decoy)
        waitUntil { onView(withText(needle)).check(matches(isDisplayed())) }

        onView(withId(R.id.action_search)).perform(click())
        onView(withId(androidx.appcompat.R.id.search_src_text))
            .perform(typeText("Needle"), closeSoftKeyboard())
        waitUntil { onView(withText(decoy)).check(doesNotExist()) }
        onView(withText(needle)).check(matches(isDisplayed()))

        onView(withText(needle)).perform(longClick())
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click())
        onView(withText(needle)).check(doesNotExist())
    }

    private fun createEntry(title: String) {
        onView(withId(R.id.fab_add)).perform(click())
        onView(withId(R.id.edit_text_title)).perform(typeText(title), closeSoftKeyboard())
        onView(withId(R.id.action_save)).perform(click())
    }

    private fun waitUntil(timeoutMs: Long = 5_000, assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            try { assertion(); return } catch (t: Throwable) {
                if (System.currentTimeMillis() > deadline) throw t
                Thread.sleep(100)
            }
        }
    }
}
