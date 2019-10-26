package com.example.julian.hanglog3;

// OpenCV installation done using instructions at https://medium.com/@sukritipaul005/a-beginners-guide-to-installing-opencv-android-in-android-studio-ea46a7b4f2d3

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
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

import org.opencv.android.OpenCVLoader;


import java.io.FileOutputStream;

public class LLog3 extends AppCompatActivity {

    //EditText outmonitor;
    TextView epicwords;
    TextView epicwords2;
    String lepicipnum = null;
    TextView epicipnum;
    EditText epicfile;

    String[] lepicipnumubx = new String[4];
    int[] lepicipnumubxPCconns = new int[4];
    int[] lepiccountubxPCmsgs = new int[4];
    TextView[] epicipnumubx = new TextView[4];
    TextView[] epicubxbytes = new TextView[4];

    int nepic = 0;
    boolean bhotspotmode;

    ReadSensor readsensor;
    RecUDP recudp;
    SocketServerThread socketserverthread;

    FileOutputStream fostream;
    ImageView tiltyview;
    CurrentPos cpos = new CurrentPos();
    GraphPlot graphplot = null;

    ReadCamera readcamera;

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

        epicipnumubx[0] = null;
        epicubxbytes[0] = null;
        epicipnumubx[1] = (TextView)findViewById(R.id.epicipnumubxA);
        epicubxbytes[1] = (TextView)findViewById(R.id.epicubxbytesA);
        epicipnumubx[2] = (TextView)findViewById(R.id.epicipnumubxB);
        epicubxbytes[2] = (TextView)findViewById(R.id.epicubxbytesB);
        epicipnumubx[3] = (TextView)findViewById(R.id.epicipnumubxC);
        epicubxbytes[3] = (TextView)findViewById(R.id.epicubxbytesC);

        readsensor = new ReadSensor((SensorManager)getSystemService(Context.SENSOR_SERVICE),
                                    (LocationManager)getSystemService(Context.LOCATION_SERVICE));
        //Toast.makeText(getBaseContext(), "readsensormade", Toast.LENGTH_LONG).show();
        Toast.makeText(getBaseContext(), "OpenCVLoader "+String.valueOf(OpenCVLoader.initDebug()), Toast.LENGTH_LONG).show();

        WifiManager wifimanager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bhotspotmode = (wifimanager.getWifiState() == WifiManager.WIFI_STATE_DISABLED); // no wifi then assume hotspot mode
        int ipAddress = wifimanager.getConnectionInfo().getIpAddress();
        Log.i("hhanglogIP", String.format("ipAddress %d", ipAddress));
        epicipnum.setText(String.format("ipAddress %d %d", ipAddress, wifimanager.getWifiState()));

        // soon to replace socket server technology
        socketserverthread = new SocketServerThread(this);
        socketserverthread.start();

        // older original UDP technology
        recudp = new RecUDP(readsensor.phonesensorqueue, readsensor.mstampsensorD0, this);
        readcamera = new ReadCamera(this, getApplicationContext());
        readcamera.start();

        //final Intent notificationIntent = new Intent(this, RecUDP.class);
        //final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification.Builder notificationbuilder = new Notification.Builder(this)
                .setContentTitle("hanglog3")
                .setContentText("whatever")
                .setSmallIcon(R.drawable.ic_launcher_background)
                //.setContentIntent(pendingIntent)
                .setTicker("thinggg");
        final Notification notification = notificationbuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        Log.i("hhanglog5","UDP startForeground");
        // Starting as a service (crashes)
        //recudp.startForeground(1112, notification);
        // or a direct call (works)
        recudp.onStartCommand(null, 0, 0);  // bypass whole "service" system

        Switch gologgingswitch = (Switch)findViewById(R.id.gologgingswitch);
        gologgingswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton c, boolean isChecked) {
                if (isChecked) {
                    epicfile.setBackgroundColor(0xFF88FF88);
                    epicfile.setText(recudp.StartLogging());
                } else {
                    epicfile.setBackgroundColor(0xFFFF8888);
                    recudp.StopLogging();
                }
            }
        });

        Switch gologpics = (Switch)findViewById(R.id.gologpics);
        gologpics.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton c, boolean isChecked) {
                try {
                    readcamera.gologpics(isChecked);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });


        Switch goddsocketswitch = (Switch)findViewById(R.id.goddsocketswitch);
        goddsocketswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton c, boolean isChecked) {
                if (isChecked) {
                    epicfile.setBackgroundColor(0xFF8888FF);
                    epicfile.setText(recudp.StartDDSocket());
                } else {
                    epicfile.setBackgroundColor(0xFFFF8888);
                    recudp.StopDDSocket();
                }
            }
        });

        Switch goneckrangeswitch = (Switch)findViewById(R.id.goneckrangeswitch);
        goneckrangeswitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton c, boolean isChecked) {
                graphplot.SetNeckRangeMode(isChecked);
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
