package com.aki.beetag;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import org.joda.time.DateTime;

public class TagTimePickerFragment
        extends DialogFragment
        implements TimePickerDialog.OnTimeSetListener {

    private OnTagTimePickedListener listener;

    public interface OnTagTimePickedListener {
        void onTagTimePicked(int hour, int minute);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DateTime currentTime = new DateTime();
        return new TimePickerDialog(
                getActivity(),
                this,
                currentTime.getHourOfDay(),
                currentTime.getMinuteOfHour(),
                DateFormat.is24HourFormat(getActivity()));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        listener = (OnTagTimePickedListener) context;
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
        listener.onTagTimePicked(hour, minute);
    }
}