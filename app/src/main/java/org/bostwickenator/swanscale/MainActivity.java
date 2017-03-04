package org.bostwickenator.swanscale;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SwanDataListener {

    SwanComms mSawnComms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSawnComms = new SwanComms(this);
        mSawnComms.addSwanDataListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSawnComms.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSawnComms.disconnect();
    }

    @Override
    public void onWeightUpdate(final double kilograms) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView)findViewById(R.id.textViewWeight)).setText("" + kilograms + "kg");
            }
        });
    }

    @Override
    public void onConnectionStateUpdate(final SwanConnectionState newState) {

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView)findViewById(R.id.textViewConnectionState)).setText(newState.name());
            }
        });
    }
}
