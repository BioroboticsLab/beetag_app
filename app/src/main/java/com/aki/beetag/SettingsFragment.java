package com.aki.beetag;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Toast;

import java.io.File;

public class SettingsFragment
        extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    File databaseFile;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle argumentBundle = getArguments();
        if (argumentBundle != null) {
            String databasePath = argumentBundle.getString("databasePath");
            if (databasePath != null) {
                databaseFile = new File(databasePath);
            }
        }

        // load preferences
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();
        findPreference("pref_decoding_server_url")
                .setSummary(sharedPreferences.getString("pref_decoding_server_url", null));

        Preference exportDatabaseButton = findPreference("pref_export_database");
        exportDatabaseButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (databaseFile != null) {
                    try {
                        Uri databaseUri = FileProvider.getUriForFile(getActivity(),
                                BuildConfig.APPLICATION_ID + ".fileprovider",
                                databaseFile);
                        Intent exportIntent = new Intent();
                        exportIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        exportIntent.setData(databaseUri); //////setDataAndType()?
                        startActivityForResult(exportIntent, 3783463); /////// insert ID
                    } catch (IllegalArgumentException e) {
                        Toast.makeText(getActivity(), "The database file could not be accessed.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                } else {
                    Toast.makeText(getActivity(), "The database file could not be located.", Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        });
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("pref_decoding_server_url")) {
            findPreference(key).setSummary(sharedPreferences.getString(key, null));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
}
