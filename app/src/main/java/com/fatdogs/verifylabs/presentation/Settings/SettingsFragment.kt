package com.fatdogs.verifylabs.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fatdogs.verifylabs.core.util.BaseActivity
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.databinding.FragmentSettingsBinding
import com.fatdogs.verifylabs.presentation.auth.AuthBaseActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {


    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Example: setup UI listeners or observe ViewModel

        binding.tvUsername.text = preferenceHelper.getUserName()
        binding.tvApiKey.text = "API KEY :  ${preferenceHelper.getApiKey().toString().take(6)}....."
        binding.etPassword.text = preferenceHelper.getPassword()?.let {
            androidx.core.text.HtmlCompat.fromHtml(
                it,
                androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        } as Editable?
        // binding.btnSave.setOnClickListener { ... }

        binding.tvCreditsRemaining.text =
            "Credits Remaining : ${preferenceHelper.getCreditRemaining()}"

        binding.llLogout.setOnClickListener {
            // Clear login state
            preferenceHelper.setIsLoggedIn(false)
            preferenceHelper.clear()

            // Create intent to LoginActivity
            val intent = Intent(requireActivity(), AuthBaseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            // Start LoginActivity and finish current activity
            startActivity(intent)
            requireActivity().finish()
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
