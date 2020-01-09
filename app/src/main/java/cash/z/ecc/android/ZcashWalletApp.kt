package cash.z.ecc.android

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import cash.z.ecc.android.di.component.AppComponent
import cash.z.ecc.android.di.component.DaggerAppComponent
import cash.z.ecc.android.feedback.Feedback
import cash.z.ecc.android.feedback.FeedbackCoordinator
import cash.z.wallet.sdk.ext.TroubleshootingTwig
import cash.z.wallet.sdk.ext.Twig
import cash.z.wallet.sdk.ext.twig
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider


class ZcashWalletApp : Application(), CameraXConfig.Provider {

    @Inject
    lateinit var feedbackCoordinator: FeedbackCoordinator

    var creationTime: Long = 0
        private set

    var creationMeasured: Boolean = false

    override fun onCreate() {
        creationTime = System.currentTimeMillis()
        instance = this
        // Setup handler for uncaught exceptions.
        super.onCreate()

        component = DaggerAppComponent.factory().create(this)
        component.inject(this)
        Thread.setDefaultUncaughtExceptionHandler(ExceptionReporter(feedbackCoordinator, Thread.getDefaultUncaughtExceptionHandler()))
        Twig.plant(TroubleshootingTwig())
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
//        MultiDex.install(this)
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    companion object {
        lateinit var instance: ZcashWalletApp
        lateinit var component: AppComponent
    }

    /**
     * @param feedbackCoordinator inject a provider so that if a crash happens before configuration
     * is complete, we can lazily initialize all the feedback objects at this moment so that we
     * don't have to add any time to startup.
     */
    class ExceptionReporter(private val coordinator: FeedbackCoordinator, private val ogHandler: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread?, e: Throwable?) {
            twig("Uncaught Exception: $e")
            coordinator.feedback.report(e)
            coordinator.flush()
            // can do this if necessary but first verify that we need it
            runBlocking {
              coordinator.await()
              coordinator.feedback.stop()
            }
            ogHandler.uncaughtException(t, e)
        }
    }
}


fun ZcashWalletApp.isEmulator(): Boolean {
    val goldfish = Build.HARDWARE.contains("goldfish");
    val emu = (System.getProperty("ro.kernel.qemu", "")?.length ?: 0) > 0;
    val sdk = Build.MODEL.toLowerCase().contains("sdk")
    return goldfish || emu || sdk;
}
