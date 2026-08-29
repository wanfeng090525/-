/*
 * SettingsFragment.kt
 * M3U-centric settings screen (second tab). Only opens the preferences.
 */
package org.videolan.vlc.gui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.vlc.R
import org.videolan.vlc.gui.preferences.PreferencesActivity

class SettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.m3u_settings_fragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.settings_entry).setOnClickListener {
            activity?.startActivityForResult(Intent(activity, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
        }
    }
}