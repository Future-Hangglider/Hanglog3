package com.example.julian.hanglog3;

import android.content.Context;
import android.hardware.SensorManager;
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

import java.io.FileOutputStream;

public class LLog3 extends AppCompatActivity {

    //EditText outmonitor;
    TextView epicwords;
    TextView epicwords2;
    String lepicipnum = null;
    TextView epicipnum;
    EditText epicfile;

    String[] lepicipnumubx = new String[3];
    TextView[] epicipnumubx = new TextView[3];
    TextView[] epicubxbytes = new TextView[3];

    int nepic = 0;
    boolean bhotspotmode;

    ReadSensor readsensor;
    RecUDP recudp;
    RecUBXUDP[] recubxudp = new RecUBXUDP[3];

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

        epicipnumubx[0] = (TextView)findViewById(R.id.epicipnumubxA);
        epicubxbytes[0] = (TextView)findViewById(R.id.epicubxbytesA);
        epicipnumubx[1] = (TextView)findViewById(R.id.epicipnumubxB);
        epicubxbytes[1] = (TextView)findViewById(R.id.epicubxbytesB);
        epicipnumubx[2] = (TextView)findViewById(R.id.epicipnumubxC);
        epicubxbytes[2] = (TextView)findViewById(R.id.epicubxbytesC);

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
        for (int i = 0; i < 3; i++) {
            recubxudp[i] = new RecUBXUDP(recudp, i);
            recubxudp[i].start();
        }

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
