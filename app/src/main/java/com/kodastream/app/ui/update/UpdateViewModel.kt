package com.kodastream.app.ui.update

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.AppUpdate
import com.kodastream.app.data.UpdateChecker
import kotlinx.coroutines.launch

/**
 * Holds the update offer for the activity, so rotating, changing the theme or
 * changing the language keeps the dialog's state. The check runs once per
 * process, when this ViewModel is first created; "Later" only clears it here.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    var update by mutableStateOf<AppUpdate?>(null)
        private set

    init {
        viewModelScope.launch { update = UpdateChecker.check(getApplication()) }
    }

    fun dismiss() {
        update = null
    }

    /** "Don't show again" skips this version on later starts too. */
    fun skip() {
        update?.let { AppPrefs.setSkippedUpdateVersion(getApplication(), it.version) }
        update = null
    }
}
