package nl.teamwildenberg.SoloMetrics

import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
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


}