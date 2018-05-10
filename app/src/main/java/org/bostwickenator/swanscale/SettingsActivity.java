package org.bostwickenator.swanscale;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;

import static org.bostwickenator.swanscale.SettingsManager.PREFS_NAME;
import static org.bostwickenator.swanscale.SettingsManager.SETTING_FIT_SYNC;
import static org.bostwickenator.swanscale.SettingsManager.SETTING_METRIC_UNITS;

public class SettingsActivity extends AppCompatActivity {

    SharedPreferences mSharedPreferences;



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
        checkBox.setChecked(mSharedPreferences.getBoolean(setting, true));

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mSharedPreferences.edit().putBoolean(setting, isChecked).apply();
            }
        });
    }
}
