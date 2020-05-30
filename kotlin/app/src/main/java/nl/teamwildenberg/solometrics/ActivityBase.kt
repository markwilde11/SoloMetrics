package nl.teamwildenberg.solometrics

import android.R
import android.animation.Animator
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume


class ActivityResult(
    val resultCode: Int,
    val data: Intent?) {
}
/// KUDOS
/// https://gist.github.com/mmoczkowski/ff349309bc8d0351c9de2c099b0cdd8e
open class ActivityBase : AppCompatActivity(){

    private val permissionRequestCounter = AtomicInteger(0)
    private val permissionListeners: MutableMap<Int, CancellableContinuation<Boolean>> = mutableMapOf()

    var currentCode : Int = 0
    var resultByCode = mutableMapOf<Int, CompletableDeferred<ActivityResult?>>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        resultByCode[requestCode]?.let {
            it.complete(ActivityResult(resultCode, data))
            resultByCode.remove(requestCode)
        } ?: run {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Launches the intent allowing to process the result using await()
     *
     * @param intent the intent to be launched.
     *
     * @return Deferred<ActivityResult>
     */
    fun launchIntent(intent: Intent) : Deferred<ActivityResult?>
    {
        val activityResult = CompletableDeferred<ActivityResult?>()

        if (intent.resolveActivity(packageManager) != null) {
            val resultCode = currentCode++
            resultByCode[resultCode] = activityResult
            startActivityForResult(intent, resultCode)
        } else {
            activityResult.complete(null)
        }
        return activityResult
    }


    private fun requestPermissions(vararg permissions: String, continuation: CancellableContinuation<Boolean>, activityRequestCode: Int) {
        permissionListeners.put(activityRequestCode, continuation)
        val isRequestRequired =
            permissions
                .map { ContextCompat.checkSelfPermission(this, it) }
                .any { result -> result == PackageManager.PERMISSION_DENIED }

        if(isRequestRequired) {
            ActivityCompat.requestPermissions(this, permissions, activityRequestCode)
        } else {
             continuation.resume(true)
        }
    }



    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val isGranted = grantResults.all { result -> result == PackageManager.PERMISSION_GRANTED }
        println("onRequestPermissionsResult:  code=" + requestCode + " isGranted=" + isGranted)
        permissionListeners
            .remove(requestCode)
            ?.resume(isGranted)
    }


    suspend fun requestPermissionss(vararg permissions: String, activityRequestCode: Int): Boolean =
        suspendCancellableCoroutine {
                continuation -> requestPermissions(*permissions, continuation = continuation, activityRequestCode = activityRequestCode)
    }

    private var floatingMenuButtonList: List<LinearLayout> = listOf()
    private lateinit var floatingMainFab: FloatingActionButton
    private lateinit var floatingBgLayout: View
    protected fun initFloatingMenu(bgLayout:View, mainFab: FloatingActionButton, buttonList: List<LinearLayout>){
        floatingMenuButtonList = buttonList
        floatingMainFab = mainFab
        floatingBgLayout = bgLayout

        mainFab.setOnClickListener {
            if (View.GONE == bgLayout.visibility) {
                expandFABMenu()
            } else {
                collapseFABMenu()
            }
        }

        bgLayout.setOnClickListener { collapseFABMenu() }
    }

    protected fun expandFABMenu() {
        var animationOffset: Int = 50;
        floatingMainFab.animate().rotationBy(180f)
        floatingBgLayout.visibility = View.VISIBLE
        floatingMenuButtonList.forEach{ fab ->
            animationOffset += 125
            fab.visibility = View.VISIBLE
            fab.animate().translationY(-animationOffset.toFloat())
        }

    }

    protected  fun collapseFABMenu() {
        lateinit var lastFab: LinearLayout
        floatingBgLayout.visibility = View.GONE
        floatingMainFab.animate().rotation(0F)
        floatingMenuButtonList.forEach { fab ->
            fab.animate().translationY(0f)
            lastFab = fab
        }
        lastFab.animate().translationY(0f)
            .setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    if (View.GONE == floatingBgLayout.visibility) {
                        floatingMenuButtonList.forEach { fab ->
                            fab.visibility = View.GONE
                        }
                    }
                }

                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationRepeat(animator: Animator) {}
            })
    }

}