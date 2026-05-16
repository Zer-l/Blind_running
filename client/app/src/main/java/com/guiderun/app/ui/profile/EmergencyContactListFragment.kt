package com.guiderun.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guiderun.app.R
import com.guiderun.app.accessibility.TtsManager
import com.guiderun.app.accessibility.voice.VoiceCommand
import com.guiderun.app.accessibility.voice.bindVoiceCommands
import com.guiderun.app.databinding.FragmentEmergencyContactListBinding
import com.guiderun.app.databinding.ItemEmergencyContactBinding
import com.guiderun.app.domain.model.EmergencyContact
import com.guiderun.app.util.EdgeToEdgeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EmergencyContactListFragment : Fragment() {

    private var _binding: FragmentEmergencyContactListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmergencyContactListViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TtsManager

    private val adapter = EmergencyContactAdapter(
        onEdit = { index -> navigateToEdit(index) },
        onDelete = { index -> viewModel.deleteContact(index) },
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEmergencyContactListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EdgeToEdgeHelper.applyInsets(view)
        ttsManager.acquire()

        setupRecyclerView()
        setupFab()
        setupVoiceCommands()
        observeUiState()

        ttsManager.speak(getString(R.string.tts_page_emergency_contact_list), TtsManager.Priority.HIGH)
        ttsManager.speak(getString(R.string.tts_hint_emergency_contact_list), TtsManager.Priority.HIGH)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadContacts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.release()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            navigateToEdit(-1)
        }
    }

    private fun setupVoiceCommands() = bindVoiceCommands { cmd ->
        when (cmd) {
            VoiceCommand.ADD_CONTACT -> { navigateToEdit(-1); true }
            VoiceCommand.CANCEL -> { findNavController().popBackStack(); true }
            else -> false
        }
    }

    private fun navigateToEdit(index: Int) {
        val bundle = Bundle().apply { putInt("index", index) }
        findNavController().navigate(R.id.action_contactList_to_contactEdit, bundle)
    }

    private var lastAnnouncedCount = -1
    private val maxContacts = 5

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.copy(error = null) } // error 单独处理，不参与去重
                    .distinctUntilChanged()
                    .collect { state ->
                        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                        // 只在加载完成后且状态变化时才处理
                        if (state.isLoaded) {
                            adapter.submitList(state.contacts)
                            val count = state.contacts.size

                            // 达到上限时禁用添加按钮
                            binding.fabAdd.isEnabled = count < maxContacts
                            binding.fabAdd.alpha = if (count < maxContacts) 1.0f else 0.5f

                            if (count != lastAnnouncedCount) {
                                lastAnnouncedCount = count
                                if (count == 0) {
                                    binding.emptyState.visibility = View.VISIBLE
                                    binding.recyclerView.visibility = View.GONE
                                    ttsManager.speak(getString(R.string.tts_contact_empty), TtsManager.Priority.NORMAL)
                                } else {
                                    binding.emptyState.visibility = View.GONE
                                    binding.recyclerView.visibility = View.VISIBLE
                                    val limitHint = if (count >= maxContacts) getString(R.string.tts_contact_limit_reached) else ""
                                    ttsManager.speak(getString(R.string.tts_contact_loaded, count, limitHint), TtsManager.Priority.NORMAL)
                                }
                            }
                        }
                    }
            }
        }

        // 单独收集错误状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.error?.let { error ->
                        val friendlyMessage = when {
                            error.contains("已达上限") || error.contains("MAX") ->
                                "紧急联系人数量已达上限，最多${maxContacts}位"
                            else -> error
                        }
                        ttsManager.speak(friendlyMessage, TtsManager.Priority.HIGH)
                    }
                }
            }
        }
    }
}

private class EmergencyContactAdapter(
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
) : ListAdapter<EmergencyContact, EmergencyContactAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemEmergencyContactBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: EmergencyContact, position: Int) {
            binding.tvName.text = contact.name
            binding.tvPhone.text = contact.phone
            binding.tvRelationship.text = contact.relationship

            binding.btnEdit.setOnClickListener { onEdit(position) }
            binding.btnDelete.setOnClickListener { onDelete(position) }

            binding.root.contentDescription = "${contact.name}，${contact.relationship}，电话${contact.phone}"
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<EmergencyContact>() {
        override fun areItemsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) =
            oldItem.phone == newItem.phone

        override fun areContentsTheSame(oldItem: EmergencyContact, newItem: EmergencyContact) =
            oldItem == newItem
    }
}
