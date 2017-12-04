package com.juniperphoton.androidmediacodecsample.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.annotation.CallSuper
import android.widget.Toast

abstract class EncodeCase(protected val context: Context) {
    companion object {
        const val RECORD_STATUS_INITIALIZED = 0
        const val RECORD_STATUS_STARTED = 1
        const val RECORD_STATUS_STOPPED = 2
    }

    var onStarted: (() -> Unit)? = null
    var onCompleted: (() -> Unit)? = null

    @Volatile
    protected var requestStop = false

    @Volatile
    var recordStatus: Int = RECORD_STATUS_INITIALIZED

    private var handler = Handler(Looper.getMainLooper())

    @CallSuper
    open fun start() {
        recordStatus = RECORD_STATUS_STARTED
    }

    @CallSuper
    open fun stop() {
        recordStatus = RECORD_STATUS_STOPPED
        requestStop = true
    }

    protected fun toast(s: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, s, Toast.LENGTH_SHORT)
        } else {
            handler.post {
                Toast.makeText(context, s, Toast.LENGTH_LONG).show()
            }
        }
    }

    protected fun fail(s: String) {
        toast(s)
        throw RuntimeException(s)
    }
}