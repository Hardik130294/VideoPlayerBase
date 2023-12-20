package com.hardik.videoplayerbase

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.hardik.videoplayerbase.databinding.FragmentFoldersBinding

class FoldersFragment : Fragment() {

    @SuppressLint("SetTextI18n")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_folders, container, false)
        val binding = FragmentFoldersBinding.bind(view)
        binding.FoldersRV.setHasFixedSize(true)
        binding.FoldersRV.setItemViewCacheSize(10)
        binding.FoldersRV.layoutManager = LinearLayoutManager(requireContext())
        try {
            binding.totalFolders.text = "${binding.totalFolders.text} ${MainActivity.folderList.size}"
            binding.FoldersRV.adapter = FoldersAdapter(requireContext(), MainActivity.folderList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return view
    }

}