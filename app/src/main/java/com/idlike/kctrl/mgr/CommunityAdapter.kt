package com.idlike.kctrl.mgr

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CommunityAdapter(
    private var items: List<CommunityFragment.CommunityItem>,
    private val onItemClick: (CommunityFragment.CommunityItem) -> Unit
) : RecyclerView.Adapter<CommunityAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        val tvAuthor: TextView = itemView.findViewById(R.id.tvAuthor)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvVersion: TextView = itemView.findViewById(R.id.tvVersion)
        val tvSize: TextView = itemView.findViewById(R.id.tvSize)
        val tvUpdatedAt: TextView = itemView.findViewById(R.id.tvUpdatedAt)
        val tagContainer: ViewGroup = itemView.findViewById(R.id.tagContainer)
        val btnDownload: View = itemView.findViewById(R.id.btnDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_community, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.tvTitle.text = item.title
        holder.tvDescription.text = item.description
        holder.tvAuthor.text = item.author
        holder.tvCategory.text = item.category
        holder.tvVersion.text = "v${item.version}"
        holder.tvSize.text = item.size
        holder.tvUpdatedAt.text = item.updatedAt
        
        // 加载图标
        Glide.with(holder.itemView.context)
            .load(item.iconUrl)
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_error)
            .into(holder.ivIcon)
        
        // 设置标签
        holder.tagContainer.removeAllViews()
        item.tags.take(3).forEach { tag ->
            val tagView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_tag, holder.tagContainer, false) as TextView
            tagView.text = tag
            holder.tagContainer.addView(tagView)
        }
        
        holder.btnDownload.setOnClickListener {
            onItemClick(item)
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<CommunityFragment.CommunityItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}