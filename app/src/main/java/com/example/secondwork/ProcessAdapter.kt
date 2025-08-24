package com.example.secondwork

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProcessAdapter(
    private var processes: List<ProcessInfoExtended> = emptyList(),
    private val onItemClick: (ProcessInfoExtended) -> Unit
) : RecyclerView.Adapter<ProcessAdapter.ProcessViewHolder>() {

    class ProcessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconImageView: ImageView = itemView.findViewById(R.id.iconImageView)
        val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        val cpuTextView: TextView = itemView.findViewById(R.id.cpuTextView)
        val memTextView: TextView = itemView.findViewById(R.id.memTextView)
        val storageTextView: TextView = itemView.findViewById(R.id.storageTextView)
        val pidTextView: TextView = itemView.findViewById(R.id.pidTextView)
        val userTextView: TextView = itemView.findViewById(R.id.userTextView)
        val romTextView: TextView = itemView.findViewById(R.id.romTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProcessViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_process, parent, false)
        return ProcessViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProcessViewHolder, position: Int) {
        val process = processes[position]

        holder.iconImageView.setImageDrawable(
            process.icon ?: holder.itemView.context.getDrawable(R.drawable.default_icon)
        )

        holder.nameTextView.text = process.name
        holder.cpuTextView.text = "CPU: ${"%.1f".format(process.cpu)}%"
        holder.memTextView.text = "RAM: ${"%.1f".format(process.mem)} MB"
        holder.storageTextView.text = "Storage: ${"%.1f".format(process.storage)} MB"
        holder.pidTextView.text = "PID: ${process.pid}"
        holder.userTextView.text = "User: ${process.user}"
        holder.romTextView.text = "ROM: ${process.rom}"

        holder.itemView.setOnClickListener { onItemClick(process) }
    }

    override fun getItemCount() = processes.size

    fun submitList(newProcesses: List<ProcessInfoExtended>) {
        processes = newProcesses
        notifyDataSetChanged()
    }
}
