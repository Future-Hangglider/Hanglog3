package com.example.julian.hanglog3;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.julian.hanglog3.GraphPlot;

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

// something about intent indexing in the manifest I don't understand
// https://developer.android.com/studio/write/app-link-indexing#java

class RecUDP extends Thread {
    DatagramSocket socket;
    DatagramPacket dp;

    // this is where we send and receive the datagram values to
    InetAddress iipnumpc = null;
    int port = 9019;

    InetAddress iipnumesp = null;
    int espport = 0;

    long mstamp0 = 0;
    int fostreamlinesP = 0;
    int fostreamlines = 0;
    FileOutputStream fostream = null;
    Queue<String> phonesensorqueue = null;
    String mstampsensorD0;
    LLog3 llog3 = null;

    byte[] dataE = null, dataP = null;
    int lengE = 0, lengP = 0;

    public RecUDP(Queue<String> lphonesensorqueue, String lmstampsensorD0, LLog3 lllog3) {
        phonesensorqueue = lphonesensorqueue;
        llog3 = lllog3;
        mstampsensorD0 = lmstampsensorD0;

        String ipnumpc = (llog3.bhotspotmode ? "192.168.43.1"   // default for android
                                             : "192.168.4.1");  // default for esp8266

        Log.i("hhanglog22", "RecUDP");
        try {
            iipnumpc = InetAddress.getByName(ipnumpc);
        } catch (UnknownHostException e) {
            Log.i("hhanglog22", e.getMessage());
        }

        byte[] lMsg = new byte[200];
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(100);
            //if (llog3.bhotspotmode)
            //    socket.bind(new InetSocketAddress(port));
        } catch (SocketException e) {
            Log.i("hhanglog15", e.getMessage());
            llog3.lepicipnum = "socketfail:"+e.getMessage();
        }
        dp = new DatagramPacket(lMsg, lMsg.length);
    }

    // these two functions open and close the logging file
    public String StartLogging() {
        File fdir = new File(Environment.getExternalStorageDirectory(), "hanglog");
        //String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

        TimeZone timeZoneUTC = TimeZone.getTimeZone("UTC");
        Calendar rightNow = Calendar.getInstance(timeZoneUTC);
        SimpleDateFormat dsdf = new SimpleDateFormat("'hdata-'yyyy-MM-dd'_'HH-mm-ss.'log'");
        dsdf.setTimeZone(timeZoneUTC);
        String fname = dsdf.format(rightNow.getTime());
        File fdata = new File(fdir, fname);

        fostreamlinesP = 0;
        fostreamlines = 0;
        try {
            fdir.mkdirs();
            fostream = new FileOutputStream(fdata);
            String header = "HangPhoneUDPlog "+fname+"\n\n";
            fostream.write(header.getBytes(), 0, header.length());
            Log.i("hhanglogFFout", String.valueOf(fdata));

        } catch (FileNotFoundException e) {
            Log.i("hhanglogFF", String.valueOf(e));
            return (e.toString() + " Please set storage permissions");
        } catch (IOException e) {
            Log.i("hhanglogI", String.valueOf(e));
            return (e.toString());
        }
        phonesensorqueue.clear();
        phonesensorqueue.add(String.format("aRt%08Xd\"%s\"\n", 0, mstampsensorD0));

        return fdata.toString();
        //return fdir + "\n" + fname;
    }

    public String StopLogging() {
        if (fostream != null) {
            FileOutputStream lfostream = fostream;
            fostream = null;
            try {
                String footer = String.format("End(%d,%d)\n", fostreamlines, fostreamlinesP);
                lfostream.write(footer.getBytes(), 0, footer.length());
                lfostream.flush();
                lfostream.close();
                Log.i("hhanglogFFout", footer);
            } catch (IOException e) {
                Log.i("hhanglogI", String.valueOf(e));
                return (e.toString() + " Please set storage permissions");
            }
        }
        return "closed";
    }

    public void writefostream(byte[] data, int leng) throws IOException {
        FileOutputStream lfostream = fostream; // protect null pointers from thread conditions
        if (lfostream != null)
            lfostream.write(data, 0, leng);

        long mstamp = System.currentTimeMillis();
        llog3.cpos.processPos(data, leng);

        if (data[0] == 'a') {
            fostreamlinesP++;
            dataP = data;
            lengP = leng;
        } else {
            fostreamlines++;
            dataE = data;
            lengE = leng;
        }

        if ((mstamp > mstamp0)) {
            final String dstringP = (dataP != null ? new String(dataP, 0, lengP) : null);
            dataP = null;
            final String dstringE = (dataE != null ? new String(dataE, 0, lengE) : null);
            dataE = null;

            llog3.runOnUiThread(new Runnable() { // need to run settext on main UI thread only
                @Override
                public void run() {
                    if (dstringE != null)
                        llog3.epicwords.setText(String.format("(%d) %s", fostreamlines, dstringE));
                    if (dstringP != null)
                        llog3.epicwords2.setText(String.format("(%d) %s", fostreamlinesP, dstringP));
                    llog3.drawtilty();
                    if (llog3.lepicipnum != null) {
                        llog3.epicipnum.setText(llog3.lepicipnum);
                        llog3.lepicipnum = null;
                    }
                }
            });
            mstamp0 = mstamp + 250;
        }
    }

    // this gets to the threading object which sits on socket.receive
    public String msgtosend = null;
    @Override
    public void run() {
        while (true) {
            boolean bgood = false;
            try {
                // write up anything from the phone sensors into the log file
                while (true) {
                    String phonesensorrep = phonesensorqueue.poll();
                    if (phonesensorrep == null)
                        break;
                    writefostream(phonesensorrep.getBytes(), phonesensorrep.length());
                }

                if ((msgtosend != null) && (socket != null)) {  // first send a message to the ESP UDP channel so it knows we want to hear from it.
                    String lmsgtosend = msgtosend;
                    Log.i("hhanglogE", lmsgtosend);
                    msgtosend = null;
                    llog3.lepicipnum = String.format("s %s:%d", iipnumpc.getHostAddress(), port);
                    socket.send(new DatagramPacket(lmsgtosend.getBytes(), lmsgtosend.length(), iipnumpc, port));
                }

                if (socket != null) {
                    //
                    socket.receive(dp); // this then timesout after 100
                    //

                    llog3.lepicipnum = String.format("r %s:%d", dp.getAddress().getHostAddress(), dp.getPort());
                    Log.i("hhanglogG",dp.toString());
                    writefostream(dp.getData(), dp.getLength());
                    bgood = true;
                }
            } catch (SocketTimeoutException e) {
                Log.i("hanglog7", String.valueOf(e));
            } catch (SocketException e) {
                Log.i("hhanglog5", e.getMessage());
            } catch (IOException e) {
                Log.i("hhanglog6", e.getMessage());
            }

            if (!bgood) {   // avoid busy loop on excepting
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    Log.i("hhanglogI", e.getMessage());
                }
            }
        }
    }
}


public class LLog3 extends AppCompatActivity {

    //EditText outmonitor;
    TextView epicwords;
    TextView epicwords2;
    String lepicipnum = null;
    TextView epicipnum;
    EditText epicfile;

    int nepic = 0;
    boolean bhotspotmode;

    ReadSensor readsensor;
    RecUDP recudp;
    FileOutputStream fostream;
    ImageView tiltyview;
    CurrentPos cpos = new CurrentPos();
    GraphPlot graphplot = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_llog3);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //outmonitor = (EditText)findViewById(R.id.outmonitor);
        epicwords = (TextView)findViewById(R.id.epicwords);
        epicwords2 = (TextView)findViewById(R.id.epicwords2);
        epicipnum = (TextView)findViewById(R.id.epicipnum);
        epicfile = (EditText)findViewById(R.id.epicfile);
        tiltyview = (ImageView)findViewById(R.id.tiltyview); // too early to make tiltycanvas

        readsensor = new ReadSensor((SensorManager)getSystemService(Context.SENSOR_SERVICE),
                                    (LocationManager)getSystemService(Context.LOCATION_SERVICE));
        Toast.makeText(getBaseContext(), "readsensormade", Toast.LENGTH_LONG).show();

        WifiManager wifimanager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bhotspotmode = (wifimanager.getWifiState() == WifiManager.WIFI_STATE_DISABLED); // no wifi then assume hotspot mode
        int ipAddress = wifimanager.getConnectionInfo().getIpAddress();
        Log.i("hhanglogIP", String.format("ipAddress %d", ipAddress));
        epicipnum.setText(String.format("ipAddress %d %d", ipAddress, wifimanager.getWifiState()));

        recudp = new RecUDP(readsensor.phonesensorqueue, readsensor.mstampsensorD0, this);
        recudp.start();

        Switch gologgingswitch = (Switch)findViewById(R.id.gologgingswitch);
        gologgingswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton c, boolean isChecked) {
                epicfile.setText(isChecked ? recudp.StartLogging() : recudp.StopLogging());
            }
        });


        Button btn = (Button)findViewById(R.id.btnepic);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("hhanglogE", "epic");
                String str = "epic" + String.valueOf(nepic++)+"\n";
                //outmonitor.append(str);
                if (recudp != null)
                    recudp.msgtosend = str;
            }
        });

        epicwords.setText("yeep\n");
    }

    float ang = 0;
    void drawtilty() {
        if (graphplot == null) {
            if ((tiltyview.getWidth() == 0) || (tiltyview.getHeight() == 0))
                return;
            graphplot = new GraphPlot(this, tiltyview, cpos);
        }
        graphplot.drawstuff();
        tiltyview.invalidate();
    }
}
