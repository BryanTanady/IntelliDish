package com.example.intellidish

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.intellidish.api.NetworkClient
import com.example.intellidish.databinding.ActivityGetRecommendationBinding
import com.example.intellidish.models.Recipe
import com.example.intellidish.utils.NetworkUtils
import com.example.intellidish.utils.UserManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class GetRecommendation : AppCompatActivity() {
    private val selectedIngredients = mutableListOf<String>()
    private lateinit var loadingDialog: AlertDialog
    private lateinit var binding: ActivityGetRecommendationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGetRecommendationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupLoadingDialog()

        // Connect the generate button click to getRecommendation()
        binding.btnGenerateRecipe.setOnClickListener {
            handleGenerateRecipeClick()
        }

        // ... rest of your onCreate code ...
    }

    private fun handleGenerateRecipeClick() {
        if (selectedIngredients.isEmpty()) {
            showError("Please select at least one ingredient")
        } else {
            getRecommendation()
        }
    }

    private fun setupLoadingDialog() {
        loadingDialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()
    }

    private fun showLoading() {
        loadingDialog.show()
    }

    private fun hideLoading() {
        loadingDialog.dismiss()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showRecipeDialog(recipe: Recipe) {
        MaterialAlertDialogBuilder(this)
            .setTitle(recipe.name)
            .setMessage(buildRecipeDetails(recipe))
            .setPositiveButton("Save") { _, _ ->
                saveRecipe(recipe)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun buildRecipeDetails(recipe: Recipe): String {
        return buildString {
            appendLine("Ingredients:")
            recipe.ingredients?.forEach { appendLine("• $it") }
            appendLine("\nInstructions:")
            recipe.instructions?.forEachIndexed { index, instruction ->
                appendLine("${index + 1}. $instruction")
            }
        }
    }

    private fun saveRecipe(recipe: Recipe) {
        lifecycleScope.launch {
            try {
                NetworkUtils.safeApiCallDirect {
                    NetworkClient.apiService.createRecipe(recipe)
                }.fold(
                    onSuccess = {
                        Snackbar.make(findViewById(android.R.id.content), "Recipe saved!", Snackbar.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        showError("Failed to save recipe: ${e.message}")
                    }
                )
            } catch (e: IOException) {
                showError("Network error: ${e.message}")
            }
        }
    }

    private fun getRecommendation() {
        showLoading()
        lifecycleScope.launch {
            try {
                // Create request body with just the ingredients list as required by the AI endpoint
                val requestBody = mapOf(
                    "ingredients" to selectedIngredients
                )

                NetworkUtils.safeApiCallDirect {
                    NetworkClient.apiService.generateAIRecipe(requestBody)
                }.fold(
                    onSuccess = { recipe ->
                        hideLoading()
                        showRecipeDialog(recipe)
                    },
                    onFailure = { e ->
                        hideLoading()
                        showError("Failed to generate recipe: ${e.message}")
                    }
                )
            } catch (e: IOException) {
                hideLoading()
                showError("Network error: ${e.message}")
            }
        }
    }

    // Add method to update selected ingredients
//    fun updateSelectedIngredients(ingredients: List<String>) {
//        selectedIngredients.clear()
//        selectedIngredients.addAll(ingredients)
//    }
} 