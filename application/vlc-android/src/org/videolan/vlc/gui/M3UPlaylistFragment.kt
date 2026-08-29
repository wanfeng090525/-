/*
 * M3UPlaylistFragment.kt
 * M3U-centric video landing screen.
 * A persistent channel list is shown after importing a local .m3u playlist or an m3u link.
 * The top-right gear button opens the import chooser.
 */
package org.videolan.vlc.gui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import java.net.URL

data class Channel(val name: String, val uri: String)

class M3UPlaylistFragment : Fragment() {

    private val channels = ArrayList<Channel>()
    private lateinit var adapter: ChannelAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.m3u_playlist_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.settings_btn).setOnClickListener { showImportChooser() }
        adapter = ChannelAdapter(channels)
        view.findViewById<RecyclerView>(R.id.channel_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@M3UPlaylistFragment.adapter
        }
        loadSaved()
        updateEmptyState()
    }

    private fun showImportChooser() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_choose_source)
                .setItems(arrayOf(getString(R.string.import_local), getString(R.string.import_remote))) { _, which ->
                    if (which == 0) importLocal() else showUrlDialog()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun importLocal() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_PICK_M3U)
    }

    private fun showUrlDialog() {
        val input = EditText(requireContext())
        input.hint = "http://example.com/playlist.m3u"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.url_m3u)
                .setView(input)
                .setPositiveButton(R.string.play) { _, _ ->
                    val url = input.text.toString().trim()
                    if (url.isNotEmpty()) importRemote(url)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PICK_M3U && resultCode == Activity.RESULT_OK && data?.data != null) {
            importRemote("content://" + data.data?.lastPathSegment, data.data!!)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun importRemote(displayName: String, localUri: Uri? = null) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                if (localUri != null) {
                    runCatching {
                        requireContext().contentResolver.openInputStream(localUri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                } else {
                    runCatching {
                        URL(displayName).openStream().bufferedReader().use { it.readText() }
                    }.getOrNull()
                }
            }
            val list = parsed?.let { parseM3U(it) } ?: emptyList()
            if (list.isNotEmpty()) {
                channels.addAll(list)
                save()
                adapter.notifyItemRangeInserted(channels.size - list.size, list.size)
                updateEmptyState()
            } else {
                Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseM3U(body: String): List<Channel> {
        val result = ArrayList<Channel>()
        var pendingName: String? = null
        for (raw in body.split("\n")) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val idx = line.lastIndexOf(',')
                    if (idx >= 0 && idx < line.length - 1) pendingName = line.substring(idx + 1).trim()
                }
                line.startsWith("#") -> { /* other directives / comments */ }
                line.isNotEmpty() -> {
                    result.add(Channel(pendingName?.ifEmpty { null } ?: line, line))
                    pendingName = null
                }
            }
        }
        return result
    }

    private fun playChannel(channel: Channel) {
        if (channel.uri.isEmpty()) return
        lifecycleScope.launch {
            val uri = Uri.parse(channel.uri)
            val resolved = withContext(Dispatchers.IO) { FileUtils.getUri(uri) } ?: uri
            val media = MLServiceLocator.getAbstractMediaWrapper(resolved)
            MediaUtils.openMedia(requireContext(), media)
        }
    }

    private fun prefs() = requireContext().getSharedPreferences("m3u_player", Context.MODE_PRIVATE)

    private fun loadSaved() {
        channels.clear()
        val json = prefs().getString(KEY_CHANNELS, null) ?: return
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                channels.add(Channel(o.getString("name"), o.getString("uri")))
            }
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun save() {
        val arr = JSONArray()
        for (c in channels) {
            val o = JSONObject()
            o.put("name", c.name)
            o.put("uri", c.uri)
            arr.put(o)
        }
        prefs().edit().putString(KEY_CHANNELS, arr.toString()).apply()
    }

    private fun updateEmptyState() {
        (view?.findViewById<View>(R.id.empty_hint))?.visibility =
                if (channels.isEmpty()) View.VISIBLE else View.GONE
    }

    inner class ChannelAdapter(private val list: ArrayList<Channel>) : RecyclerView.Adapter<ChannelAdapter.VH>() {
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.m3u_channel_item, parent, false)
            return VH(v)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.itemView.findViewById<android.widget.TextView>(R.id.m3u_item_name).text = item.name
            holder.itemView.setOnClickListener { playChannel(item) }
        }
    }

    companion object {
        private const val REQUEST_PICK_M3U = 0xC0FFEE
        private const val KEY_CHANNELS = "channels"
    }
}