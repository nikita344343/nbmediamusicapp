package com.wordpresssync.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wordpresssync.app.api.model.CatalogItem
import com.wordpresssync.app.databinding.ItemCatalogBinding

class CatalogAdapter(private var items: List<CatalogItem>) : RecyclerView.Adapter<CatalogAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCatalogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<CatalogItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(private val binding: ItemCatalogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CatalogItem) {
            binding.catalogTitle.text = item.title
            binding.catalogArtist.text = item.artist
            binding.catalogDate.text = item.date
            binding.catalogStatus.text = item.status ?: ""
            binding.catalogPlays.text = if (item.totalPlays > 0) "▶ ${item.totalPlays}" else ""
        }
    }
}
