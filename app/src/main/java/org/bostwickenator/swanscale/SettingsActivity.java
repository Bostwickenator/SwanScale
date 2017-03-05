package org.bostwickenator.swanscale;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;

public class SettingsActivity extends AppCompatActivity {

    SharedPreferences mSharedPreferences;

    public static final String PREFS_NAME = "prefs";
    public static final String SETTING_FIT_SYNC = "fitsync";
    public static final String SETTING_METRIC_UNITS = "metric_units";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mSharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupCheckbox(R.id.switchGoogleFitSync, SETTING_FIT_SYNC);
        setupCheckbox(R.id.switchMetricUnits, SETTING_METRIC_UNITS);
    }

    private void setupCheckbox(int id, final String setting) {
        Switch checkBox = (Switch) findViewById(id);
        checkBox.setChecked(mSharedPreferences.getBoolean(setting, false));

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSharedPreferences.edit().putBoolean(setting, isChecked).apply();
            }
        });
    }
}
