package io.sentry.android.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.Hub
import io.sentry.android.core.internal.gestures.NoOpWindowCallback
import io.sentry.android.core.internal.gestures.SentryWindowCallback
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UserInteractionIntegrationTest {

    private class Fixture {
        val application = mock<Application>()
        val hub = mock<Hub>()
        val options = SentryAndroidOptions().apply {
            dsn = "https://key@sentry.io/proj"
        }
        val activity = mock<Activity>()
        val window = mock<Window>()
        val loadClass = mock<LoadClass>()

        fun getSut(
            callback: Window.Callback? = null,
            isAndroidXAvailable: Boolean = true
        ): UserInteractionIntegration {
            whenever(loadClass.isClassAvailable(any(), anyOrNull<SentryAndroidOptions>())).thenReturn(isAndroidXAvailable)
            whenever(hub.options).thenReturn(options)
            whenever(window.callback).thenReturn(callback)
            whenever(activity.window).thenReturn(window)

            val resources = mockResources()
            whenever(activity.resources).thenReturn(resources)
            return UserInteractionIntegration(application, loadClass)
        }

        companion object {
            fun mockResources(): Resources {
                val displayMetrics = mock<DisplayMetrics>()
                displayMetrics.density = 1.0f

                val resources = mock<Resources>()
                whenever(resources.displayMetrics).thenReturn(displayMetrics)
                return resources
            }
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when user interaction breadcrumb is enabled registers a callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        verify(fixture.application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when user interaction breadcrumb is disabled doesn't register a callback`() {
        val sut = fixture.getSut()
        fixture.options.isEnableUserInteractionBreadcrumbs = false

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when UserInteractionIntegration is closed unregisters the callback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.close()

        verify(fixture.application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when androidx is unavailable doesn't register a callback`() {
        val sut = fixture.getSut(isAndroidXAvailable = false)

        sut.register(fixture.hub, fixture.options)

        verify(fixture.application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `registers window callback on activity resumed`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityResumed(fixture.activity)

        val argumentCaptor = argumentCaptor<Window.Callback>()
        verify(fixture.window).callback = argumentCaptor.capture()
        assertTrue { argumentCaptor.firstValue is SentryWindowCallback }
    }

    @Test
    fun `when no original callback delegates to NoOpWindowCallback`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        sut.onActivityResumed(fixture.activity)

        val argumentCaptor = argumentCaptor<Window.Callback>()
        verify(fixture.window).callback = argumentCaptor.capture()
        assertTrue {
            argumentCaptor.firstValue is SentryWindowCallback &&
                (argumentCaptor.firstValue as SentryWindowCallback).delegate is NoOpWindowCallback
        }
    }

    @Test
    fun `unregisters window callback on activity paused`() {
        val context = mock<Context>()
        val resources = Fixture.mockResources()
        whenever(context.resources).thenReturn(resources)
        val sut = fixture.getSut(
            SentryWindowCallback(
                NoOpWindowCallback(),
                context,
                mock(),
                mock()
            )
        )

        sut.onActivityPaused(fixture.activity)

        verify(fixture.window).callback = null
    }

    @Test
    fun `preserves original callback on activity paused`() {
        val delegate = mock<Window.Callback>()
        val context = mock<Context>()
        val resources = Fixture.mockResources()
        whenever(context.resources).thenReturn(resources)
        val sut = fixture.getSut(
            SentryWindowCallback(
                delegate,
                context,
                mock(),
                mock()
            )
        )

        sut.onActivityPaused(fixture.activity)

        verify(fixture.window).callback = delegate
    }
}
