package com.example.julian.hanglog3;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

// Will we be able to tell if we are a hotspot, or are connecting to the esp hotspot?
// and make the UDP connection appropriately?

// Isolated class with its management and output
class ReadSensor implements SensorEventListener, LocationListener {
    long mstampsensor0 = 0;
    public String mstampsensorD0;
    long mstampmidnight0 = 0;

    SensorManager mSensorManager;
    Sensor mSensorRotvector;
    Sensor mSensorPressure;
    LocationManager mLocationManager;

    Queue<String> phonesensorqueue = new ConcurrentLinkedQueue<String>();
    int phonesensorqueuesizelimit = 10;

    public ReadSensor(SensorManager lmSensorManager, LocationManager lmLocationManager) {
        //mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        TimeZone timeZoneUTC = TimeZone.getTimeZone("UTC");
        Calendar rightNow = Calendar.getInstance(timeZoneUTC);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        sdf.setTimeZone(timeZoneUTC);
        mstampsensor0 = rightNow.getTimeInMillis();
        mstampsensorD0 = sdf.format(rightNow.getTime());
        rightNow.set(Calendar.HOUR_OF_DAY, 0);
        rightNow.set(Calendar.MINUTE, 0);
        rightNow.set(Calendar.SECOND, 0);
        rightNow.set(Calendar.MILLISECOND, 0);
        mstampmidnight0 = rightNow.getTimeInMillis();
        Log.i("hhanglogDD", "midnightmillis "+mstampmidnight0+" dayoffs "+(mstampsensor0-mstampmidnight0));

        mSensorManager = lmSensorManager;
        mSensorRotvector =  mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, mSensorRotvector, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorPressure = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        mSensorManager.registerListener(this, mSensorPressure, SensorManager.SENSOR_DELAY_NORMAL);

        mLocationManager = lmLocationManager;
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 50, 1, this);
        } catch (SecurityException e) {
            Log.i("hhanglogS", e.toString());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor snsr, int accuracy) {
        Log.i("hanglogS", snsr.toString());
    }

    @Override
    public void onSensorChanged(SensorEvent evt) {
        long tstamp = System.currentTimeMillis() - mstampsensor0;
        //Log.i("hanglog", String.valueOf(evt.values.length));
        // use https://stackoverflow.com/questions/41408704/how-to-convert-game-rotation-vector-sensor-result-to-axis-angles
        String phonesensorrep = null;
        if (evt.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR)
            phonesensorrep = String.format("aZt%08Xx%04Xy%04Xz%04X\n", tstamp, ((int)(evt.values[0]*32768))&0xFFFF, ((int)(evt.values[1]*32768))&0xFFFF, ((int)(evt.values[2]*32768))&0xFFFF);
        else if (evt.sensor.getType() == Sensor.TYPE_PRESSURE)
            phonesensorrep = String.format("aFt%08Xp%06X\n", tstamp, ((int)(evt.values[0]*100))&0xFFFFFF);
        if ((phonesensorrep != null) && (phonesensorqueue.size() < phonesensorqueuesizelimit))
            phonesensorqueue.add(phonesensorrep);
    }

    public void cameraCharucoPosition(long tval, double rvec0, double rvec1, double rvec2, double tvec0, double tvec1, double tvec2) {
        long tstamp = tval - mstampsensor0;
        //long tstamp = System.currentTimeMillis() - mstampsensor0;
        long lrvec0 = Double.doubleToRawLongBits(rvec0);
        long lrvec1 = Double.doubleToRawLongBits(rvec1);
        long lrvec2 = Double.doubleToRawLongBits(rvec2);
        long ltvec0 = Double.doubleToRawLongBits(tvec0);
        long ltvec1 = Double.doubleToRawLongBits(tvec1);
        long ltvec2 = Double.doubleToRawLongBits(tvec2);
        String chs = String.format("aCt%08Xa%16Xb%16Xc%16Xd%16Xe%16Xf%16X\n", tstamp, lrvec0, lrvec1, lrvec2, ltvec0, ltvec1, ltvec2);
        if (phonesensorqueue.size() < phonesensorqueuesizelimit)
            phonesensorqueue.add(chs);
    }

    // GPS sensor functions
    @Override
    public void onProviderDisabled(String provider) {
        Log.i("hhanglogLD", provider);
    }
    @Override
    public void onProviderEnabled(String provider) {
        Log.i("hhanglogLE", provider);
    }
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.i("hhanglogLES", provider);
    }

    @Override
    public void onLocationChanged(Location loc) {
        long tstamp = System.currentTimeMillis() - mstampsensor0;
        Log.i("hhanglogL", "lat: " + loc.getLatitude() + "lng:" + loc.getLongitude());
        int latminutes10000 = (int)Math.round(loc.getLatitude()*60*10000);
        int lngminutes10000 = (int)Math.round(loc.getLongitude()*60*10000);
        int altitude10 = (int)Math.round(loc.getAltitude()*10);
        long mstampmidnight = loc.getTime() - mstampmidnight0;
        String phonesensorrep = String.format("aQt%08Xu%08Xy%08Xx%08Xa%04X\n", tstamp, mstampmidnight, latminutes10000&0xFFFFFFFF, lngminutes10000&0xFFFFFFFF, altitude10&0xFFFF);
        if ((phonesensorrep != null) && (phonesensorqueue.size() < phonesensorqueuesizelimit))
            phonesensorqueue.add(phonesensorrep);

        if (loc.hasSpeed() && (phonesensorrep != null) && (phonesensorqueue.size() < phonesensorqueuesizelimit)) {
            String phonesensorrepV = String.format("aVt%08Xv%04Xd%04X\n", tstamp, ((int)(loc.getSpeed())*100)&0xFFFF, ((int)(loc.getBearing()*10))&0xFFFF);
            phonesensorqueue.add(phonesensorrepV);
        }
    }
}
