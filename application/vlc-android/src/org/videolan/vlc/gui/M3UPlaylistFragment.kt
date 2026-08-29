/*
 * M3UPlaylistFragment.kt
 * M3U-centric landing screen: pick a local .m3u playlist or paste an m3u link.
 */
package org.videolan.vlc.gui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.vlc.R
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils

class M3UPlaylistFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.m3u_playlist_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.local_file_card).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(intent, REQUEST_PICK_M3U)
        }
        view.findViewById<View>(R.id.url_card).setOnClickListener { showUrlDialog() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PICK_M3U && resultCode == android.app.Activity.RESULT_OK && data?.data != null) {
            playSource(data.data!!)
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
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
                    if (url.isNotEmpty()) playSource(Uri.parse(url))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun playSource(uri: Uri) {
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) { FileUtils.getUri(uri) } ?: uri
            val media = MLServiceLocator.getAbstractMediaWrapper(resolved)
            MediaUtils.openMedia(requireContext(), media)
        }
    }

    companion object {
        private const val REQUEST_PICK_M3U = 0xC0FFEE
    }
}