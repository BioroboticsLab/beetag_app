package com.aki.beetag;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.widget.DatePicker;

import org.joda.time.DateTime;

public class TagDatePickerFragment
        extends DialogFragment
        implements DatePickerDialog.OnDateSetListener {

    private TagDatePickerFragment.OnTagDatePickedListener listener;

    public interface OnTagDatePickedListener {
        void onTagDatePicked(int year, int month, int day);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DateTime currentTime = new DateTime();
        return new DatePickerDialog(
                getActivity(),
                this,
                currentTime.getYear(),
                currentTime.getMonthOfYear(),
                currentTime.getDayOfMonth());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (TagDatePickerFragment.OnTagDatePickedListener) context;
    }

    @Override
    public void onDateSet(DatePicker datePicker, int year, int month, int day) {
        listener.onTagDatePicked(year, month + 1, day);
    }
}
