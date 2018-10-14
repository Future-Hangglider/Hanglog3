package com.example.julian.hanglog3;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


// Isolated class with its management and output
class ReadSensor implements SensorEventListener {
    SensorManager mSensorManager;
    Sensor mSensorRotvector;
    Sensor mSensorPressure;
    long mstampsensor0 = 0;

    Queue<String> phonesensorqueue = new ConcurrentLinkedQueue<String>();

    public ReadSensor(SensorManager lmSensorManager) {
        //mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mstampsensor0 = System.currentTimeMillis();
        mSensorManager = lmSensorManager;
        mSensorRotvector =  mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, mSensorRotvector, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorPressure = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        mSensorManager.registerListener(this, mSensorPressure, SensorManager.SENSOR_DELAY_NORMAL);
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
            phonesensorrep = String.format(":Zt%08Xx%04Xy%04Xz%04X\n", tstamp, ((int)(evt.values[0]*32768))&0xFFFF, ((int)(evt.values[1]*32768))&0xFFFF, ((int)(evt.values[2]*32768))&0xFFFF);
        else if (evt.sensor.getType() == Sensor.TYPE_PRESSURE)
            phonesensorrep = String.format(":Ft%08Xp%06X\n", tstamp, ((int)(evt.values[0]*100))&0xFFFFFF);
        if ((phonesensorrep != null) && (phonesensorqueue.size() < 10))
            phonesensorqueue.add(phonesensorrep);
    }
}


// something about intent indexing in the manifest I don't understand
// https://developer.android.com/studio/write/app-link-indexing#java

class RecUDP extends Thread {
    DatagramSocket socket;
    DatagramPacket dp;

    String ipnumpc = "192.168.4.1";
    int port = 9019;
    InetAddress iipnumpc = null;
    long mstamp0 = 0;
    int fostreamlinesP = 0;
    int fostreamlines = 0;
    FileOutputStream fostream = null;
    Queue<String> phonesensorqueue = null;
    EditText epicwords = null;
    Activity act = null;

    public RecUDP(Queue<String> lphonesensorqueue, Activity lact, EditText lepicwords) {
        phonesensorqueue = lphonesensorqueue;
        act = lact;
        epicwords = lepicwords;
        long mstampsensor0 = 0;

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
        } catch (SocketException e) {
            Log.i("hhanglog15", e.getMessage());
        }
        dp = new DatagramPacket(lMsg, lMsg.length);

    }

    // these two functions open and close the logging file
    public String StartLogging() {
        File fdir = new File(Environment.getExternalStorageDirectory(), "hanglog");
        String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File fdata = new File(fdir, "hdata" + "-" + currentDateandTime + ".log");
        fostreamlinesP = 0;
        fostreamlines = 0;
        try {
            fdir.mkdirs();
            fostream = new FileOutputStream(fdata);
            String header = "HangPhoneUDPlog "+currentDateandTime+"\n\n";
            fostream.write(header.getBytes(), 0, header.length());
        } catch (FileNotFoundException e) {
            Log.i("hhanglogFF", String.valueOf(e));
            return (e.toString() + " Please set storage permissions");
        } catch (IOException e) {
            Log.i("hhanglogI", String.valueOf(e));
            return (e.toString());
        }
        return fdata.toString();
    }

    public String StopLogging() {
        if (fostream != null) {
            FileOutputStream lfostream = fostream;
            fostream = null;
            try {
                lfostream.close();
            } catch (IOException e) {
                Log.i("hhanglogI", String.valueOf(e));
                return (e.toString() + " Please set storage permissions");
            }
        }
        return "closed";
    }

    public void writefostream(byte[] data, int leng) throws IOException {
        fostreamlines++;
        if (data[0] == ':')
            fostreamlinesP++;
        long mstamp = System.currentTimeMillis();
        if (mstamp > mstamp0) {
            final String lText = String.format("(%d,%d) ", fostreamlines, fostreamlinesP) + new String(data, 0, leng);
            Log.i("hhanglogD", lText);
            act.runOnUiThread(new Runnable() { // need to run settext on main UI thread only
                @Override
                public void run() {
                    epicwords.setText(lText);
                }
            });
            mstamp0 = mstamp + 1000;
        }
        FileOutputStream lfostream = fostream; // protect null pointers from thread conditions
        if (lfostream != null)
            lfostream.write(data, 0, leng);
    }

    // this gets to the threading object
    public String msgtosend = null;
    @Override
    public void run() {
        while (true) {
            boolean bgood = false;
            try {
                // write up anything from the phone sensors into the log file
                String phonesensorrep = phonesensorqueue.poll();
                if (phonesensorrep != null)
                    writefostream(phonesensorrep.getBytes(), phonesensorrep.length());

                if (msgtosend != null) {  // first send a message to the ESP UDP channel so it knows we want to hear from it.
                    String lmsgtosend = msgtosend;
                    Log.i("hhanglogE", lmsgtosend);
                    msgtosend = null;
                    socket.send(new DatagramPacket(lmsgtosend.getBytes(), lmsgtosend.length(), iipnumpc, port));
                }
                socket.receive(dp); // this then timesout
                writefostream(dp.getData(), dp.getLength());
                bgood = true;
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

    EditText outmonitor;
    EditText epicwords;
    EditText epicfile;

    int nepic = 0;

    ReadSensor readsensor;
    RecUDP recudp;
    FileOutputStream fostream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_llog3);

        outmonitor = (EditText)findViewById(R.id.outmonitor);
        epicwords = (EditText)findViewById(R.id.epicwords);
        epicfile = (EditText)findViewById(R.id.epicfile);

        readsensor = new ReadSensor((SensorManager)getSystemService(Context.SENSOR_SERVICE));
        recudp = new RecUDP(readsensor.phonesensorqueue, this, epicwords);
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
                outmonitor.append(str);
                if (recudp != null)
                    recudp.msgtosend = str;
            }
        });

        epicwords.setText("yeep\n");
    }
}
