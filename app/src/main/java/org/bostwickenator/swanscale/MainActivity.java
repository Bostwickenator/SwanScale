package org.bostwickenator.swanscale;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SwanDataListener {

    private static final String TAG = "MainActivity";

    SwanComms mSawnComms;
    GoogleApiClient mFitnessClient;
    FloatingActionButton fabSave;
    double finalWeight = -1;
    Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mContext = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mSawnComms = new SwanComms(this);
        mSawnComms.addSwanDataListener(this);

        fabSave = (FloatingActionButton)findViewById(R.id.fabSave);
        fabSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (finalWeight > 0) {
                    saveWeight(finalWeight);
                } else {
                    Toast.makeText(mContext, R.string.no_data, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(checkPermissions()) {
            mSawnComms.connect();
            buildFitnessClient();
        } else {
            requestPermissions();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSawnComms.disconnect();
    }

    private boolean checkPermissions() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        return permission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                7);

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

    @Override
    public void onFinalWeight(double kilograms) {
        finalWeight = kilograms;
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
               fabSave.setVisibility(View.VISIBLE);
            }
        });
    }

    private void saveWeight(final double kilograms) {
        if (!mFitnessClient.isConnected()) {
            return;
        }
        updateSyncing(true);
        Date now = new Date();
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(this)
                .setDataType(DataType.TYPE_WEIGHT)
                .setStreamName(TAG + " - weight")
                .setType(DataSource.TYPE_RAW)
                .build();

        DataSet dataSet = DataSet.create(dataSource);
        DataPoint dataPoint = dataSet.createDataPoint().setTimestamp(now.getTime(), TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_WEIGHT).setFloat((float)kilograms);
        dataSet.add(dataPoint);
        PendingResult<Status> result = Fitness.HistoryApi.insertData(mFitnessClient, dataSet);
        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                Log.i(TAG, "weight uploaded: " + status.getStatus().isSuccess());
                updateSyncing(false);
                fabSave.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void updateSyncing(final boolean syncing) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.progressBarGoogleFit).setVisibility(syncing ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    private void buildFitnessClient() {
        if (mFitnessClient != null) {
            return;
        }
        mFitnessClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_BODY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Fitness Api connected");
                                getLastWeight();
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i
                                        == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG,
                                            "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .enableAutoManage(this, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.i(TAG, "Google Play services connection failed. Cause: " +
                                result.toString());
                    }
                })
                .build();
    }


    private void setWeekPeriod(DataReadRequest.Builder builder) {
        Date now = new Date();
        Calendar cal = new GregorianCalendar();
        cal.setTime(now);
        long end = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_WEEK, -7);
        long start = cal.getTimeInMillis();
        builder.setTimeRange(start, end, TimeUnit.MILLISECONDS);
    }

    private void getLastWeight() {
        DataReadRequest.Builder builder = new DataReadRequest.Builder();
        builder.read(DataType.TYPE_WEIGHT);
        builder.setLimit(1);
        setWeekPeriod(builder);
        PendingResult<DataReadResult> result = Fitness.HistoryApi.readData(mFitnessClient, builder.build());
        result.setResultCallback(new ResultCallback<DataReadResult>() {
                                     @Override
                                     public void onResult(DataReadResult dataReadResult) {
                                         DataSet weight = dataReadResult.getDataSet(DataType.TYPE_WEIGHT);
                                         DataPoint point = weight.getDataPoints().get(0);
                                         float kilograms = point.getValue(Field.FIELD_WEIGHT).asFloat();
                                         setLastWeight(kilograms);
                                     }
                                 }
        );
    }

    private void setLastWeight(final float kilograms) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView)findViewById(R.id.textViewLastWeight)).setText("" + kilograms + "kg");
            }
        });
    }
}
