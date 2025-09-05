package com.fatdogs.verifylabs.core.util

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB

    // Each child activity must implement this to provide its binding
    abstract fun getViewBinding(): VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = getViewBinding()
        setContentView(binding.root)
        initObj()
        click()
    }

    // Child activities can initialize objects here
    abstract fun initObj()

    // Child activities can handle clicks here
    abstract fun click()
}
