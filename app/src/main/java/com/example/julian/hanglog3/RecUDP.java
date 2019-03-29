package com.example.julian.hanglog3;

// something about intent indexing in the manifest I don't understand
// https://developer.android.com/studio/write/app-link-indexing#java

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Queue;
import java.util.TimeZone;


class SocketServerReplyThread extends Thread {
    Socket hostThreadSocket;
    int threadcount;
    LLog3 llog3;
    int ubxI = -1;
    char ubxILetter;
    RecUDP recudp;

    SocketServerReplyThread(Socket socket, int lthreadcount, LLog3 lllog3) {
        hostThreadSocket = socket;
        threadcount = lthreadcount;
        llog3 = lllog3;
        recudp = llog3.recudp;
    }

    @Override
    public void run() {
        try {
            OutputStream outputStream = hostThreadSocket.getOutputStream();
            InputStream inputStream = hostThreadSocket.getInputStream();
            outputStream.write((String.format("Hello from Android thread %d\n", threadcount)).getBytes());
            int h0 = inputStream.read();
            int h1 = inputStream.read();
            int h2 = inputStream.read();
            int h3 = inputStream.read();
            Log.i("hhanglogX", String.format("thread%d bytes %x %x %x %x", threadcount, h0, h1, h2, h3));

            // connection from ESP32 with data; @ABC
            if ((h0 == h1) && (h0 == h2) && (h0 == h3) && (h0 >= '@') && (h0 <= 'C')) {
                ubxI = h0 - '@';
                ubxILetter = (char)h0;

                if (recudp.fosocketthread[ubxI] != null) {
                    recudp.fosocketthread[ubxI].closethesocket();
                }
                recudp.fosocketthread[ubxI] = this;
                recudp.llog3.lepicipnumubx[ubxI] = String.format("r%s %s:%d", ubxILetter, hostThreadSocket.getInetAddress().getCanonicalHostName(), -1);

                byte[] buff = new byte[1000];
                StringBuffer sb0 = (ubxI == 0 ? new StringBuffer() : null);
                while (true) {
                    int x = inputStream.read(buff);
                    if (x != -1) {
                        if (sb0 != null) {
                            sb0.append(new String(buff, 0, x));
                            while (true) {
                                int il = sb0.indexOf("\n");
                                if (il == -1)
                                    break;
                                recudp.writefostream(sb0.substring(0, il+1).getBytes(), il+1);
                                sb0.delete(0, il+1);
                            }
                        } else {
                            //Log.i("hhanglogX", String.format("thread %d bytes %d %x", threadcount, x, buff[0]));
                            recudp.writefostreamUBX(ubxI, buff, x);
                        }
                        sleep(10);
                    }
                }

            // connection in from PC wanting data to be forwarded to it (hand over socket and terminate thread)
            } else if ((h0 == '-') && (h1 == h2) && (h1 == h3) && (h1 >= '@') && (h1 <= 'C')) {
                int subxI = h1 - '@';
                Log.i("hhanglogX pcforward", String.format("thread %d ubxI %d", threadcount, subxI));
                if (recudp.pcforwardsockets[subxI] != null) {
                    try {
                        recudp.pcforwardsockets[subxI].close();
                    } catch (IOException e) {
                        ;
                    }
                }
                recudp.pcforwardsockets[subxI] = hostThreadSocket;
                recudp.pcforwardoutputStreams[subxI] = recudp.pcforwardsockets[subxI].getOutputStream();
                hostThreadSocket = null;
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if ((ubxI != -1) && (hostThreadSocket != null)) {
            try {
                hostThreadSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.i("hhanglogX", "closed");
            recudp.fosocketthread[ubxI] = null;
        }
    }

    public void closethesocket() {
        Log.i("hhanglogX", "closing");
        try {
            hostThreadSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


// Now make a serversocket version to transition from UDP technology
class SocketServerThread extends Thread {
    LLog3 llog3;

    SocketServerThread(LLog3 lllog3) {
        llog3 = lllog3;
    }

    @Override
    public void run() {
        int threadcount = 0;
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(9042);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        while (true) {
            try {
                Log.i("hhanglogW", "serverSocket.accepting next "+threadcount);
                Socket hostThreadSocket = serverSocket.accept();
                Log.i("hhanglogW connnectionmade", hostThreadSocket.getInetAddress().toString()+" thread:"+threadcount);
                SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(hostThreadSocket, threadcount, llog3);
                socketServerReplyThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            threadcount += 1;
        }
    }
}



class RecUDP extends Thread {
    DatagramSocket socket;
    DatagramPacket dp;

    // this is where we send and receive the datagram values to
    InetAddress iipnumpc = null;
    int port = 9019;

    int[] ubxbytesP = {0, 0, 0, 0};

    long mstamp0 = 0;
    int fostreamlinesP = 0;
    int fostreamlines = 0;
    String fname = null;
    File[] fdataUBX = new File[4];
    FileOutputStream[] fostreamUBX = new FileOutputStream[4];
    SocketServerReplyThread[] fosocketthread = new SocketServerReplyThread[4];
    Socket[] pcforwardsockets = new Socket[4];
    OutputStream[] pcforwardoutputStreams = new OutputStream[4];

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

        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(800);
            //if (llog3.bhotspotmode)
            //    socket.bind(new InetSocketAddress(port));
        } catch (SocketException e) {
            Log.i("hhanglog15", e.getMessage());
            llog3.lepicipnum = "socketfail:"+e.getMessage();
        }
        byte[] lMsg = new byte[200];
        dp = new DatagramPacket(lMsg, lMsg.length);
    }

    // these two functions open and close the logging file
    // * you need to disconnect from AndroidStudio before you can access the files from PC usb
    public String StartLogging() {
        File fdir = new File(Environment.getExternalStorageDirectory(), "hanglog");
        //File fdir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        TimeZone timeZoneUTC = TimeZone.getTimeZone("UTC");
        Calendar rightNow = Calendar.getInstance(timeZoneUTC);

        SimpleDateFormat ddsdf = new SimpleDateFormat("yyyyMMddHHmmss");
        ddsdf.setTimeZone(timeZoneUTC);
        File ddir = new File(fdir, ddsdf.format(rightNow.getTime()));
        //String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

        SimpleDateFormat dsdf = new SimpleDateFormat("'hdata-'yyyy-MM-dd'_'HH-mm-ss");
        dsdf.setTimeZone(timeZoneUTC);
        fname = dsdf.format(rightNow.getTime());
        fdataUBX[0] = new File(ddir, fname + ".log");
        fdataUBX[1] = new File(ddir, fname + "A.ubx");
        fdataUBX[2] = new File(ddir, fname + "B.ubx");
        fdataUBX[3] = new File(ddir, fname + "C.ubx");
        fostreamlinesP = 0;
        fostreamlines = 0;
        ubxbytesP[0] = 0;
        ubxbytesP[1] = 0;
        ubxbytesP[2] = 0;
        ubxbytesP[3] = 0;

        try {
            ddir.mkdirs();
            fostreamUBX[0] = new FileOutputStream(fdataUBX[0]);  // log file
            String header = "HangPhoneUDPlog "+fname+"\n\n";
            fostreamUBX[0].write(header.getBytes(), 0, header.length());
            Log.i("hhanglogFFout", String.valueOf(fostreamUBX[0]));
            for (int i = 1; i < 4; i++)
                fostreamUBX[i] = null;
        } catch (FileNotFoundException e) {
            Log.i("hhanglogFF", String.valueOf(e));
            return (e.toString() + " Please set storage permissions");
        } catch (IOException e) {
            Log.i("hhanglogI", String.valueOf(e));
            return (e.toString());
        }
        phonesensorqueue.clear();
        phonesensorqueue.add(String.format("aRt%08Xd\"%s\"\n", 0, mstampsensorD0));

        return fdataUBX[0].toString();
        //return fdir + "\n" + fname;
    }

    public String StopLogging() {
        for (int i = 0; i < 4; i++) {
            FileOutputStream lfostreamUBX = fostreamUBX[i];
            if (lfostreamUBX != null) {
                fostreamUBX[i] = null;
                fdataUBX[i] = null;
                try {
                    if (i == 0) {
                        String footer = String.format("End(%d,%d)\n", fostreamlines, fostreamlinesP);
                        lfostreamUBX.write(footer.getBytes(), 0, footer.length());
                    }
                    lfostreamUBX.flush();
                    lfostreamUBX.close();
                } catch (IOException e) {
                    Log.i("hhanglogIubx", String.valueOf(e));
                    return (e.toString() + " Please set storage permissions");
                }
            }
        }
        return "closed";
    }

    // this is where we separate out multiple UBX streams from different ipnumbers
    public void writefostreamUBX(int ubxI, byte[] data, int leng) throws IOException {
        try {
        if ((fdataUBX[ubxI] != null) && (fostreamUBX[ubxI] == null) && (ubxbytesP[ubxI] == 0))
            fostreamUBX[ubxI] = new FileOutputStream(fdataUBX[ubxI]);
        } catch (FileNotFoundException e) {
            Log.i("hhanglogFFu", String.valueOf(e));
        }
        FileOutputStream lfostreamUBX = fostreamUBX[ubxI];
        if (lfostreamUBX != null)
            lfostreamUBX.write(data, 0, leng);

        ubxbytesP[ubxI] += leng;

        Socket lpcforwardsocket = pcforwardsockets[ubxI];
        if (lpcforwardsocket != null) {
            try {
                pcforwardoutputStreams[ubxI].write(data, 0, leng);
            } catch (IOException e) {
                pcforwardsockets[ubxI] = null;
                pcforwardoutputStreams[ubxI] = null;
                lpcforwardsocket.close();
            }
        }


    }

    public void writefostream(byte[] data, int leng) throws IOException {
        FileOutputStream lfostream = fostreamUBX[0]; // protect null pointers from thread conditions
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


        // update the numbers in the UI at a quarter second rate
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

                    for (int i = 1; i < 4; i++) {
                        if (llog3.lepicipnumubx[i] != null) {
                            llog3.epicipnumubx[i].setText(llog3.lepicipnumubx[i]);
                            llog3.lepicipnumubx[i] = null;
                        }
                        llog3.epicubxbytes[i].setText(String.format("UBX(%d)", ubxbytesP[i]));
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
                    socket.receive(dp); // this timesout after 100

                    llog3.lepicipnum = String.format("r %s:%d", dp.getAddress().getHostAddress(), dp.getPort());
                    Log.i("hhanglogG", dp.toString());
                    writefostream(dp.getData(), dp.getLength());
                    bgood = true;
                }
            } catch (SocketTimeoutException e) {
                ; //Log.i("hanglog7", String.valueOf(e));
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

