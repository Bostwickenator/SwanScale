package org.bostwickenator.swanscale;

/**
 * Created by Alex on 2017-03-03.
 */
public interface SwanDataListener {

    public void onWeightUpdate(double kilograms);
    public void onFinalWeight(double kilograms);
    public void onConnectionStateUpdate(SwanConnectionState newState);
}
