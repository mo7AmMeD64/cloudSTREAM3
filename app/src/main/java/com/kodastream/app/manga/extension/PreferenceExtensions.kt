@file:Suppress("PackageDirectoryMismatch")

package androidx.preference

/**
 * Local bridge to package-private preference hooks, same as Mihon's
 * `PreferenceExtensions.kt` (Apache-2.0). Extensions configure these through the
 * library API; the screen only needs to read them.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun EditTextPreference.getOnBindEditTextListener(): EditTextPreference.OnBindEditTextListener? =
    onBindEditTextListener

/** Calls [onChange] whenever this preference's state, visibility or children change. */
fun Preference.setOnStateChangeListener(onChange: (Preference) -> Unit) {
    setOnPreferenceChangeInternalListener(
        object : Preference.OnPreferenceChangeInternalListener {
            override fun onPreferenceChange(preference: Preference) = onChange(preference)
            override fun onPreferenceHierarchyChange(preference: Preference) = onChange(preference)
            override fun onPreferenceVisibilityChange(preference: Preference) = onChange(preference)
        },
    )
}
