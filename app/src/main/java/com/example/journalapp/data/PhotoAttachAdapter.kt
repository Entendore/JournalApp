package com.example.journalapp.data

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.journalapp.databinding.ItemPhotoThumbBinding

class PhotoAttachAdapter(
    private val names: List<String>,
    private val onRemove: (Int) -> Unit,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoAttachAdapter.Holder>() {

    class Holder(val binding: ItemPhotoThumbBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemPhotoThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = names.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val name = names[position]
        holder.binding.image.load(PhotoStore.file(holder.binding.root.context, name)) { crossfade(true) }
        holder.binding.buttonRemove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
        holder.binding.root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }
}
