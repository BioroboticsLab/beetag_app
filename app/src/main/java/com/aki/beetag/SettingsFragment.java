package com.aki.beetag;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
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

        Preference exportDatabaseButton = findPreference("pref_export_database");
        exportDatabaseButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (databaseFile != null) {
                    // copy database file to 'Downloads' folder
                    File downloadsFolder = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File databaseCopy = new File(downloadsFolder, "beetags.db");
                    if (downloadsFolder.canWrite()) { // maybe replace by try-catch, could return false
                        try (FileChannel fromChannel =
                                     new FileInputStream(databaseFile).getChannel();
                             FileChannel toChannel =
                                     new FileOutputStream(databaseCopy).getChannel()) {
                            long transferCount = toChannel.transferFrom(fromChannel, 0, fromChannel.size());
                            if (transferCount != 0) {
                                Toast.makeText(getActivity(), "Database successfully copied!", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getActivity(), "Something went wrong while copying the database.", Toast.LENGTH_LONG).show();
                            }
                        } catch (FileNotFoundException e) {
                            Toast.makeText(getActivity(), "Could not find database file.", Toast.LENGTH_LONG).show();
                        } catch (IOException e) {
                            Toast.makeText(getActivity(), "Something went wrong while reading the database.", Toast.LENGTH_LONG).show();
                        }
                    }

                    /*
                    Uri databaseUri = FileProvider.getUriForFile(getActivity(),
                            BuildConfig.APPLICATION_ID + ".fileprovider",
                            databaseCopy);
                    Intent exportIntent = new Intent();
                    exportIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    exportIntent.setData(databaseUri); //////setDataAndType()?
                    startActivityForResult(exportIntent, 3783463); /////// insert ID
                    */

                    ///////Toast.makeText(getActivity(), "The database file could not be accessed.", Toast.LENGTH_LONG).show();
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
