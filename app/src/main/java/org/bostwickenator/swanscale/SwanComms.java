package org.bostwickenator.swanscale;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Created by Alex on 2017-03-03.
 */

public class SwanComms {

    UUID WEIGHT_SERVICE_UUID = UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb");
    UUID WEIGHT_CHARACTERISTIC_UUID = UUID.fromString("0000ffb2-0000-1000-8000-00805f9b34fb");


    //BYTE 0 always AC
    //BYTE 1
    //BYTE 2 values
    final int BODY_FAT = 0xFE;
    final int ADC = 0xFD;
    final int USER_ID = 0xFC;
    final int MCU_DATE = 0xFB;
    final int MCU_TIME = 0xFA;
    //anything else is weight

    //BYTE 6 datatype?
    //BYTE 7 checksum?

    Context mContext;
    BluetoothManager mBluetoothManager;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothLeScanner mBluetoothLeScanner;
    ScanCallback mScanCallback;
    BluetoothDevice mBluetoothDevice;
    BluetoothGattCallback mBluetoothGattCallback;
    BluetoothGatt mBluetoothGatt;

    BluetoothGattCharacteristic weightCharacteristic;

    List<SwanDataListener> mListeners;

    SwanConnectionState mConnectionState;

    boolean shouldConnect = false;

    double lastWeight = -1.0;

    public SwanComms(Context context) {
        mContext = context;
        mListeners = new ArrayList<>();
    }

    public void connect() {
        shouldConnect = true;
        mBluetoothManager = (BluetoothManager)mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();


        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            connectionStateUpdate(SwanConnectionState.BLUETOOTH_DISABLED);
            return;
        }

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        createScanCallback();
        createBluetoothGattCallback();
        beginScan();
    }

    public void disconnect() {
        shouldConnect = false;
        if (mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
        }
        if(mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }

    public void addSwanDataListener(SwanDataListener listener) {
        mListeners.add(listener);
    }

    private void beginScan() {
        mBluetoothLeScanner.startScan(Collections.singletonList(getSwanScanFilter()), getScanSettings(), mScanCallback);
        connectionStateUpdate(SwanConnectionState.SEARCHING);
    }

    private void createBluetoothGattCallback() {
        if(mBluetoothGattCallback != null)
            return;
        mBluetoothGattCallback = new BluetoothGattCallback() {
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                setupToReceiveNotifications();
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                processSwanData();
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                switch (newState) {
                    case BluetoothGatt.STATE_CONNECTED:
                        connectionStateUpdate(SwanConnectionState.CONNECTING);
                        mBluetoothGatt = gatt;
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        connectionStateUpdate(SwanConnectionState.DISCONNECTED);
                        if (shouldConnect) {
                            beginScan();
                        }
                        break;
                }
            }
        };
    }

    private boolean checkChecksum(byte[] value) {
        int checkSum = 0;
        for (int i = 2; i < 7; i++) {
            checkSum += value[i];
        }
        int result = checkSum & 0xFF;
        return result == (value[7] & 0xFF);
    }

    private void processSwanData() {
        byte[] value = weightCharacteristic.getValue();

        System.out.println(Utils.bytesToHex(value));

        if(checkChecksum(value) == false){
            System.out.println("Checksum fail");
            return;
        }

        int type = (value[2] & 0xFF);

        switch (type) {
            case ADC:
                if (value[3] == 1) {
                    System.out.println("adc " + (((value[4] & 0xFF) << 8) + (value[5] & 0xFF)) / 10.0f);
                }
                weightFinalized();
                break;
            default:
                if (type < 0xF0) {
                    ByteBuffer wrapped = ByteBuffer.wrap(value);
                    int decagrams = wrapped.getShort(2);
                    double kilograms = decagrams / 10.0;
                    System.out.println("kilos  " + kilograms);
                    System.out.println("pounds " + 2.20462 * kilograms);
                    weightUpdate(kilograms);
                }
                break;
        }
    }

    private void weightFinalized() {
        for (SwanDataListener listener: mListeners) {
            listener.onFinalWeight(lastWeight);
        }
    }

    private void weightUpdate(double kilograms) {
        lastWeight = kilograms;
        for (SwanDataListener listener: mListeners) {
            listener.onWeightUpdate(kilograms);
        }
    }

    private void connectionStateUpdate(SwanConnectionState newState) {
        mConnectionState = newState;
        for (SwanDataListener listener: mListeners) {
            listener.onConnectionStateUpdate(newState);
        }
    }

    private void setupToReceiveNotifications() {
        BluetoothGattService weightService = mBluetoothGatt.getService(WEIGHT_SERVICE_UUID);
        weightCharacteristic = weightService.getCharacteristic(WEIGHT_CHARACTERISTIC_UUID);
        mBluetoothGatt.setCharacteristicNotification(weightCharacteristic, true);
        connectionStateUpdate(SwanConnectionState.CONNECTED);
    }

    private void createScanCallback() {
        if(mScanCallback != null)
            return;
        mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                mBluetoothLeScanner.stopScan(mScanCallback);
                mBluetoothDevice = result.getDevice();
                mBluetoothDevice.connectGatt(mContext, false, mBluetoothGattCallback);
            }
        };
    }

    private ScanSettings getScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        //builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        return builder.build();
    }

    private ScanFilter getSwanScanFilter() {
        ScanFilter.Builder builder = new ScanFilter.Builder();
        builder.setDeviceName("SWAN");
        return builder.build();
    }
}
