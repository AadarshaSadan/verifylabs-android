package com.fatdogs.verifylabs.core.util

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.atomic.AtomicBoolean

class SingleLiveEvent<T> : MutableLiveData<T>() {

    private val mPending = AtomicBoolean(false)

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {

        if (hasActiveObservers()) {
            Log.w(TAG, "Multiple observers registered but only one will be notified of changes.")
        }

        // Observe the internal MutableLiveData
        super.observe(owner, object : Observer<T> {

            override fun onChanged(value: T) {
                if (mPending.compareAndSet(true, false)) {
                    observer.onChanged(value)
                }
            }

        })
    }

    @MainThread
    override fun setValue(t: T?) {
        mPending.set(true)
        super.setValue(t)
    }

    override fun postValue(value: T) {
        mPending.set(true)
        super.postValue(value)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        setValue(null)
    }

    companion object {

        private val TAG = "SingleLiveEvent"
    }
}
fun View.showSnackbar(message: String) {
    // Create a snackbar
    Snackbar
        .make(
            this,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
}
fun View.showSnakbarAtTop(message: String){
    val snackBarView = Snackbar.make(this, message , Snackbar.LENGTH_LONG)
    val view = snackBarView.view
    val params = view.layoutParams as FrameLayout.LayoutParams
    params.gravity = Gravity.TOP
    view.layoutParams = params
    snackBarView.show()
}
fun View.clickWithDebounce(debounceTime: Long = 800L, action: () -> Unit) {
    this.setOnClickListener(object : View.OnClickListener {
        private var lastClickTime: Long = 0

        override fun onClick(v: View) {
            if (SystemClock.elapsedRealtime() - lastClickTime < debounceTime) return
            else action()

            lastClickTime = SystemClock.elapsedRealtime()
        }
    })
}
fun View.showView(){
    this.visibility=View.VISIBLE
}
fun View.hideView(){
    this.visibility=View.GONE
}
fun Snackbar.withColor(@ColorInt colorInt: Int): Snackbar{
    this.view.setBackgroundColor(colorInt)
    return this
}

fun ShowToast(context:Context,message: String)
{
    Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
}




