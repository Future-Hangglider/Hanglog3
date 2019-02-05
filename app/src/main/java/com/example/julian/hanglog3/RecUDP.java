package com.example.julian.hanglog3;

// something about intent indexing in the manifest I don't understand
// https://developer.android.com/studio/write/app-link-indexing#java

import android.os.Environment;
import android.util.Log;

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
import java.util.Calendar;
import java.util.Queue;
import java.util.TimeZone;

class RecUBXUDP extends Thread {
    RecUDP recudp;

    RecUBXUDP(RecUDP lrecudp)
    {
        recudp = lrecudp;
    }

    @Override
    public void run() {
        while (true) {
            boolean bgood = false;
            try {
                if (recudp.socketUBX != null) {
                    recudp.socketUBX.receive(recudp.dpUBX); // this then timesout after 100
                    recudp.llog3.lepicipnumubx = String.format("r %s:%d", recudp.dpUBX.getAddress().getHostAddress(), recudp.dpUBX.getPort());
                    recudp.ubxbytesP += recudp.dpUBX.getLength();
                    bgood = true;
                }
            } catch (SocketTimeoutException e) {
                Log.i("hanglog7ubx", String.valueOf(e));
            } catch (SocketException e) {
                Log.i("hhanglog5ubx", e.getMessage());
            } catch (IOException e) {
                Log.i("hhanglog6ubx", e.getMessage());
            }

            if (!bgood) {   // avoid busy loop on excepting
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    Log.i("hhanglogIubx", e.getMessage());
                }
            }
        }
    }
}


class RecUDP extends Thread {
    DatagramSocket socket;
    DatagramPacket dp;

    // this is where we send and receive the datagram values to
    InetAddress iipnumpc = null;
    int port = 9019;

    DatagramSocket socketUBX;
    DatagramPacket dpUBX;
    int portUBX = 9020;
    int ubxbytesP = 0;

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
        byte[] lMsgUBX = new byte[1900];
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(100);
            //if (llog3.bhotspotmode)
            //    socket.bind(new InetSocketAddress(port));

            socketUBX = new DatagramSocket(portUBX);
            socketUBX .setSoTimeout(100);

        } catch (SocketException e) {
            Log.i("hhanglog15", e.getMessage());
            llog3.lepicipnum = "socketfail:"+e.getMessage();
        }
        dp = new DatagramPacket(lMsg, lMsg.length);
        dpUBX = new DatagramPacket(lMsgUBX, lMsgUBX.length);
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

                    if (llog3.lepicipnumubx != null) {
                        llog3.epicipnumubx.setText(llog3.lepicipnumubx);
                        llog3.lepicipnumubx = null;
                    };
                    llog3.epicubxwords.setText(String.format("UBX(%d)", ubxbytesP));
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
                    Log.i("hhanglogG", dp.toString());
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

