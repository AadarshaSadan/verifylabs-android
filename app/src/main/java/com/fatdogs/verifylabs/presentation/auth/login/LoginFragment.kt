package com.fatdogs.verifylabs.presentation.auth.login

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fatdogs.verifylabs.core.util.Resource
import com.fatdogs.verifylabs.core.util.Status
import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.data.base.PreferencesHelperImpl
import com.fatdogs.verifylabs.databinding.FragmentLoginBinding
import com.fatdogs.verifylabs.presentation.MainActivity
import com.fatdogs.verifylabs.presentation.model.PostResponse
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    @Inject
    lateinit var preferenceHelper: PreferenceHelper
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var loginViewModel: LoginViewModel




    private val TAG = "LoginFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        // Handle login button click
        binding.btnGetStarted.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty()) {
                binding.etUsername.error = "Please enter username"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.etPassword.error = "Please enter password"
                return@setOnClickListener
            }

            //set username in shared preferences
            preferenceHelper.setUserName(username)
            preferenceHelper.setPassword(password)

            // Call login API
            loginViewModel.login(username, password)
        }

        // Optional: Handle "Create Account" click
        binding.btnCreateAccount.setOnClickListener {
            Toast.makeText(requireContext(), "Navigate to Signup", Toast.LENGTH_SHORT).show()
        }

        // Observe login response
        loginViewModel.getLoginResponse().observe(viewLifecycleOwner) { resource ->
            when (resource.status) {
                Status.LOADING -> {
                    // Optionally show loading indicator
                    Toast.makeText(requireContext(), "Logging in...", Toast.LENGTH_SHORT).show()
                }

                Status.SUCCESS -> {
                        Toast.makeText(requireContext(), "Login Successful", Toast.LENGTH_SHORT).show()
                        if (resource.data != null) {
                            // Handle successful login, e.g., navigate to main activity
                            Toast.makeText(requireContext(), "${resource.data}", Toast.LENGTH_SHORT)
                                .show()
                            try {
                                val response = Gson().fromJson(
                                    resource.data.toString(),
                                    apiResponseLogin::class.java
                                )
                                preferenceHelper.setApiKey(response.apiKey)
                                preferenceHelper.setIsLoggedIn(true)
                                preferenceHelper.setCreditReamaining(response.credits)
                                navigateToMainActivity(requireActivity())


                            }catch (e:Exception){
                                Toast.makeText(requireContext(), "Parsing error: ${e.message}", Toast.LENGTH_SHORT)
                                    .show()
                            }

                        }



                }

                Status.ERROR -> {
                    Toast.makeText(
                        requireContext(),
                        "Login Failed: ${resource.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }


        }
    }

    private fun navigateToMainActivity(activity: android.app.Activity) {
        val intent = android.content.Intent(activity, MainActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity.finish()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
