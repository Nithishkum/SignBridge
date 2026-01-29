package com.example.cameraxproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(
    private val images: List<Int>,
    private val titles: List<String>,
    private val descs: List<String>
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.img)
        val title: TextView = view.findViewById(R.id.title)
        val desc: TextView = view.findViewById(R.id.desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = images.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.img.setImageResource(images[position])
        holder.title.text = titles[position]
        holder.desc.text = descs[position]
    }
}
