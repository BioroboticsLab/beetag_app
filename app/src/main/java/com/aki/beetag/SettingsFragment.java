package com.aki.beetag;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

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
        findPreference("pref_default_label")
                .setSummary(sharedPreferences.getString("pref_default_label", null));

        Preference exportDatabaseButton = findPreference("pref_export_database");
        exportDatabaseButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (databaseFile != null) {
                    // copy database file to 'Downloads' folder
                    File downloadsFolder = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File databaseCopy = new File(downloadsFolder, "beetags.db");
                    try (FileChannel fromChannel =
                                 new FileInputStream(databaseFile).getChannel();
                         FileChannel toChannel =
                                 new FileOutputStream(databaseCopy).getChannel()) {
                        long transferCount = toChannel.transferFrom(fromChannel, 0, fromChannel.size());
                        if (transferCount != 0) {
                            Toast.makeText(
                                    getActivity(),
                                    "Database successfully copied to Downloads.",
                                    Toast.LENGTH_LONG).show();

                            DatabaseShareDialogFragment shareDialog = new DatabaseShareDialogFragment();
                            shareDialog.setDatabaseFile(databaseCopy);
                            shareDialog.show(getFragmentManager(), "databaseShareDialog");
                        } else {
                            Toast.makeText(
                                    getActivity(),
                                    "Something went wrong while copying the database. (Path: " +
                                        databaseFile.getAbsolutePath() + ")",
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (FileNotFoundException e) {
                        Toast.makeText(
                                getActivity(),
                                "Could not find database file. (Path: " +
                                        databaseFile.getAbsolutePath() + ")",
                                Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Toast.makeText(
                                getActivity(),
                                "Something went wrong while reading the database. (Path: " +
                                        databaseFile.getAbsolutePath() + ")",
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(getActivity(), "The database file could not be located.", Toast.LENGTH_LONG).show();
                    return true;
                }
                return true;
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
        if (key.equals("pref_decoding_server_url") || key.equals("pref_default_label")) {
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
