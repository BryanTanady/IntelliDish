package com.example.intellidish.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.intellidish.R
import com.example.intellidish.api.ApiService
import com.example.intellidish.api.NetworkClient
import com.example.intellidish.models.Potluck
import com.example.intellidish.models.PotluckIngredient
import com.example.intellidish.models.RemoveAddIngredientsRequest
import com.example.intellidish.utils.NetworkUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PotluckIngredientAdapter(
    private val potluckId: String,
    private val currentUserName: String,
    private val participantId: String,
) : RecyclerView.Adapter<PotluckIngredientAdapter.ViewHolder>() {

    private val ingredients = mutableListOf<PotluckIngredient>()
    
    init {
        // Initial fetch using coroutine scope
        CoroutineScope(Dispatchers.IO).launch {
            try {
                fetchIngredientsFromServer()
            } catch (e: IOException) {
                Log.e("PotluckAdapter", "Error in initial fetch: ${e.message}")
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ingredientText: TextView = itemView.findViewById(R.id.ingredient_text)
        val removeButton: ImageButton = itemView.findViewById(R.id.btn_remove)
    }

    suspend fun fetchIngredientsFromServer() {
        try {
            val response = NetworkClient.apiService.getPotluckDetails(potluckId)
            if (response.isSuccessful && response.body() != null) {
                val potluckResponse = response.body()!!
                val potluck = potluckResponse.potluck
                
                val newIngredients = createIngredientList(potluck)
                updateUI(newIngredients)

            } else {
                Log.e("PotluckAdapter", "Failed to fetch potluck details: ${response.errorBody()?.string()}")
            }
        } catch (e: IOException) {
            Log.e("PotluckAdapter", "Error fetching potluck details: ${e.message}")
            throw e
        }
    }

    private fun createIngredientList(potluck: Potluck): List<PotluckIngredient> {

        val newIngredients = mutableListOf<PotluckIngredient>()
        potluck.participants.forEach { participant ->
            participant.ingredients?.forEach { ing ->
                newIngredients.add(PotluckIngredient(ing, participant.user.name))
            }
        }

        return newIngredients
    }

    private suspend fun updateUI(newIngredients: List<PotluckIngredient>) {
        withContext(Dispatchers.Main) {
            if (!ingredients.containsAll(newIngredients) || !newIngredients.containsAll(ingredients)) {
                ingredients.clear()
                ingredients.addAll(newIngredients)
                notifyDataSetChanged()

            }
        }
    }

    fun getIngredientNames(): List<String> {
        return ingredients.map { it.name }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, addedBy) = ingredients[position]

        // Display "Carrots (by Emma Wilson)" or similar
        holder.ingredientText.text = "$name (by $addedBy)"

        if (addedBy.equals(currentUserName, ignoreCase = true)) {
            // If the user owns this ingredient, show remove button
            holder.removeButton.visibility = View.VISIBLE
            holder.removeButton.setOnClickListener {
                removeIngredientFromBackend(holder.itemView.context, name, position)
            }
        } else {
            // Hide remove button for non-owners
            holder.removeButton.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = ingredients.size

    fun addIngredient(context: Context, name: String) {
        val newIngredient = PotluckIngredient(name, currentUserName)
        ingredients.add(newIngredient)
        notifyItemInserted(ingredients.size - 1)

        // Send to backend
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestBody = RemoveAddIngredientsRequest(
                    participantId = participantId,
                    ingredients = listOf(name)
                )
                val response = NetworkClient.apiService.addPotluckIngredientsToParticipant(
                    potluckId = potluckId,
                    requestBody = requestBody
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            context,
                            "Ingredient added successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        fetchIngredientsFromServer()
                    } else {
                        Log.e("PotluckAdapter", "Error adding ingredient: ${response.errorBody()?.string()}")
                        Toast.makeText(context, "Failed to add ingredient", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("PotluckAdapter", "Network error adding ingredient: ${e.message}")
            }
        }
    }

    private fun removeIngredientFromBackend(context: Context, ingredientName: String, position: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Assume you're using a Retrofit method that returns a Response<Unit>
                val requestBody = RemoveAddIngredientsRequest(
                    participantId = participantId,
                    ingredients = listOf(ingredientName)
                )
                val response = NetworkClient.apiService.removePotluckIngredientsFromParticipant(
                    potluckId = potluckId,
                    requestBody = requestBody
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        // Remove the ingredient from local list
                        Toast.makeText(context, "Ingredient removed successfully!", Toast.LENGTH_SHORT).show()
                        fetchIngredientsFromServer()

                    } else {
                        Toast.makeText(context, "Failed to remove ingredient", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Network error removing ingredient", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}

