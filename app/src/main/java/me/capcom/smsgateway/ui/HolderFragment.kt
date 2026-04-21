package me.capcom.smsgateway.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentHolderBinding

class HolderFragment : Fragment() {
    private var _binding: FragmentHolderBinding? = null
    private val binding get() = _binding!!
    private var selectedTab = TAB_OUTGOING

    companion object {
        const val TAG_OUTGOING = "tab_outgoing"
        const val TAG_INCOMING = "tab_incoming"
        const val TAG_ST904L = "tab_st904l"

        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val TAB_OUTGOING = 0
        private const val TAB_INCOMING = 1
        private const val TAB_ST904L = 2

        fun newInstance() = HolderFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parentFragmentManager.commit {
            setPrimaryNavigationFragment(this@HolderFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonOutgoing.setOnClickListener {
            selectOutgoing()
        }

        binding.buttonIncoming.setOnClickListener {
            selectIncoming()
        }

        binding.buttonSt904l.setOnClickListener {
            selectSt904l()
        }

        if (savedInstanceState == null) {
            selectOutgoing()
        } else {
            selectedTab = savedInstanceState.getInt(KEY_SELECTED_TAB, TAB_OUTGOING)
            when (selectedTab) {
                TAB_INCOMING -> selectIncoming()
                TAB_ST904L -> selectSt904l()
                else -> selectOutgoing()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTab)
    }

    private fun updateButtonStates() {
        binding.buttonOutgoing.isEnabled = selectedTab != TAB_OUTGOING
        binding.buttonIncoming.isEnabled = selectedTab != TAB_INCOMING
        binding.buttonSt904l.isEnabled = selectedTab != TAB_ST904L
    }

    private fun selectOutgoing() {
        selectedTab = TAB_OUTGOING

        updateButtonStates()
        val outgoing = childFragmentManager.findFragmentByTag(TAG_OUTGOING)
            ?: MessagesListFragment.newInstance()
        val incoming = childFragmentManager.findFragmentByTag(TAG_INCOMING)
        val st904l = childFragmentManager.findFragmentByTag(TAG_ST904L)

        childFragmentManager.commit {
            if (!outgoing.isAdded) add(R.id.rootLayout, outgoing, TAG_OUTGOING)
            incoming?.let { hide(it) }
            st904l?.let { hide(it) }
            show(outgoing)
        }
    }

    private fun selectIncoming() {
        selectedTab = TAB_INCOMING

        updateButtonStates()
        val incoming = childFragmentManager.findFragmentByTag(TAG_INCOMING)
            ?: IncomingMessagesListFragment.newInstance()
        val outgoing = childFragmentManager.findFragmentByTag(TAG_OUTGOING)
        val st904l = childFragmentManager.findFragmentByTag(TAG_ST904L)

        childFragmentManager.commit {
            if (!incoming.isAdded) add(R.id.rootLayout, incoming, TAG_INCOMING)
            outgoing?.let { hide(it) }
            st904l?.let { hide(it) }
            show(incoming)
        }
    }

    private fun selectSt904l() {
        selectedTab = TAB_ST904L

        updateButtonStates()
        val st904l = childFragmentManager.findFragmentByTag(TAG_ST904L)
            ?: ST904LFragment.newInstance()
        val outgoing = childFragmentManager.findFragmentByTag(TAG_OUTGOING)
        val incoming = childFragmentManager.findFragmentByTag(TAG_INCOMING)

        childFragmentManager.commit {
            if (!st904l.isAdded) add(R.id.rootLayout, st904l, TAG_ST904L)
            outgoing?.let { hide(it) }
            incoming?.let { hide(it) }
            show(st904l)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
