package com.aki.beetag;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

public class TagDeletionConfirmationDialogFragment extends DialogFragment {

    private OnTagDeletionConfirmedListener listener;

    public interface OnTagDeletionConfirmedListener {
        void onTagDeletionConfirmed();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Delete tag?")
                .setMessage("Are you sure you want to delete this tag, including all notes and " +
                        "labels? This action cannot be undone.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        listener.onTagDeletionConfirmed();
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
        listener = (OnTagDeletionConfirmedListener) context;
    }
}
