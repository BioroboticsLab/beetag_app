package com.aki.beetag;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import java.io.File;

public class DatabaseShareDialogFragment extends DialogFragment {

    private File databaseFile;
    private OnDatabaseShareListener listener;

    public interface OnDatabaseShareListener {
        void onDatabaseShare(File file);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Share database file?")
                .setMessage("The database file has been copied to the Downloads folder. Do " +
                        "you want to share it now?")
                .setPositiveButton("Share", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        listener.onDatabaseShare(databaseFile);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked on "Cancel"
                    }
                });
        // create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (OnDatabaseShareListener) context;
    }

    public void setDatabaseFile(File file) {
        this.databaseFile = file;
    }
}
