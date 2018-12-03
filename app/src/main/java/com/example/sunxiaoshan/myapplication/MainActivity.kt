package com.example.sunxiaoshan.myapplication

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

class MainActivity : AppCompatActivity() {

    @TargetApi(Build.VERSION_CODES.N)
    @RequiresApi(Build.VERSION_CODES.N)


    object CommonPool : Pool(ForkJoinPool.commonPool())

    private val executor = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "scheduler")
    }

    fun <T> launch(
        receiver: T,
        context: CoroutineContext,
        block: suspend T.() -> Unit)
            = block.startCoroutine(receiver, StandaloneCoroutine(context))

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


//        log("[1] before coroutine")
//        asyncCalcMd5("test.zip", "[1] hello value") {
//            log("[1]in coroutine. Before suspend.")
//            val result: String = suspendCoroutine {
//                    continuation ->
//                log("[1]in suspend block.")
//                continuation.resume(calcMd5(continuation.context[Demo]!!.value))
//                log("[1]after resume.")
//            }
//            log("[1]in coroutine. After suspend. result = $result")
//        }
//        log("[1] after coroutine")

        launchWithContext(Demo("test.zip", "Hello world") + CommonPool) {
            log("in coroutine. Before suspend.")
            val result: String = calcMd5V2(this[Demo]!!.value).await()
            log("in coroutine. After suspend. result = $result")
        }

    }

    class FilePath(val path: String): AbstractCoroutineContextElement(FilePath){
        companion object Key : CoroutineContext.Key<FilePath>
    }

    class Demo(val path: String, val value: String): AbstractCoroutineContextElement(Demo) {
        companion object Key : CoroutineContext.Key<Demo>
    }

    fun calcMd5(path: String): String {
        log("calc md5 for $path.")
        Thread.sleep(1000)
        return System.currentTimeMillis().toString()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun calcMd5V2(path: String): CompletableFuture<String> = CompletableFuture.supplyAsync {
        log("calc md5 for $path.")
        Thread.sleep(1000)
        System.currentTimeMillis().toString()
    }

    fun log(message: String) {
        val context = "[${Thread.currentThread().name}] " + message
        Log.d("test", context)
    }

    fun asyncCalcMd5(path: String, value: String, block: suspend () -> Unit) {
         val continuation = object : Continuation<Unit> {
             override val context: CoroutineContext
                 get() = Demo(path, value) + CommonPool

             override fun resumeWithException(exception: Throwable) {
                 log(exception.toString())
             }

             override fun resume(value: Unit) {
                 log("resume: $value")
             }

        }
        block.startCoroutine(continuation)
    }


    open class Pool(val pool: ForkJoinPool)
        : AbstractCoroutineContextElement(ContinuationInterceptor),
        ContinuationInterceptor {
        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
            return PoolContinuation(
                pool,
                continuation.context.fold(continuation, { cont, element ->
                    if (element != this@Pool && element is ContinuationInterceptor)
                        element.interceptContinuation(cont) else cont
                })
            )
        }
    }

    private class PoolContinuation<T>(
        val pool: ForkJoinPool,
        val continuation: Continuation<T>
    ) : Continuation<T> by continuation {
        override fun resume(value: T) {
            if (isPoolThread()) continuation.resume(value)
            else pool.execute { continuation.resume(value) }
        }

        override fun resumeWithException(exception: Throwable) {
            if (isPoolThread()) continuation.resumeWithException(exception)
            else pool.execute { continuation.resumeWithException(exception) }

        }
  
        fun isPoolThread(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (Thread.currentThread() as? ForkJoinWorkerThread)?.pool == pool
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }

    }

    class StandaloneCoroutine(override val context: CoroutineContext): Continuation<Unit> {
        override fun resume(value: Unit) {

        }

        override fun resumeWithException(exception: Throwable) {
            val currentThread = Thread.currentThread()
            currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, exception)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    suspend fun <T> CompletableFuture<T>.await(): T {
        return suspendCoroutine {
                continuation ->
            whenComplete { result, e ->
                if (e == null) continuation.resume(result)
                else continuation.resumeWithException(e)
            }
        }
    }

    fun launchWithContext (
        context: CoroutineContext,
        block: suspend CoroutineContext.() -> Unit)
            = launch(context, context, block)

}







