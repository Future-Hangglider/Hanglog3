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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
            Log.i("hhanglogX", String.format("thread%d socket incoming bytes %x %x %x %x", threadcount, h0&0xFF, h1&0xFF, h2&0xFF, h3&0xFF));

            // connection from ESP32 with data; @ABC
            // (incoming data loop)
            if ((h0 == h1) && (h0 == h2) && (h0 == h3) && (h0 >= '@') && (h0 <= 'C')) {
                ubxI = h0 - '@';
                ubxILetter = (char)h0;

                if (recudp.fosocketthread[ubxI] != null) {
                    recudp.fosocketthread[ubxI].closethesocket();
                }
                recudp.fosocketthread[ubxI] = this;
                recudp.llog3.lepicipnumubx[ubxI] = String.format("r%s %s,%d", ubxILetter, hostThreadSocket.getInetAddress().getCanonicalHostName(), llog3.lepicipnumubxPCconns[ubxI]);

                byte[] buff = new byte[1000];
                StringBuffer sb0 = (ubxI == 0 ? new StringBuffer() : null);

                while (true) {
                    int x = inputStream.read(buff);
                    if (x != -1) {
                        if (ubxI == 0) {  // ascii hanglog stream
                            sb0.append(new String(buff, 0, x));
                            while (true) {
                                int il = sb0.indexOf("\n");
                                if (il == -1)
                                    break;
                                recudp.writefostream(sb0.substring(0, il+1).getBytes(), il+1);
                                sb0.delete(0, il+1);
                            }
                        } else {  // normal UBX byte stream
                            //Log.i("hhanglogX", String.format("thread %d bytes %d %x", threadcount, x, buff[0]));
                            recudp.writefostreamUBX(ubxI, buff, x);
                        }
                        sleep(10);
                    }
                }

            // connection in from PC wanting data to be forwarded to it (may be multiple connections)
            // (outgoing data handover)
            } else if ((h0 == '-') && (h1 == h2) && (h1 == h3) && (h1 >= '@') && (h1 <= 'C')) {
                int subxI = h1 - '@';
                Log.i("hhanglogX pcforward", String.format("thread %d ubxI %d", threadcount, subxI));

                int i = 0;
                while (i < recudp.Npcforwardsockets) {
                    if (recudp.pcforwardsockets[i] == null)
                        break;
                    i++;
                }
                if (i == recudp.Npcforwardsockets) {
                    if (recudp.Npcforwardsockets < 10)
                        recudp.Npcforwardsockets++;
                    else
                        i = 0;
                }
                Log.i("hhanglogX pcforward", String.format("thread %d ubxI %d i%d", threadcount, subxI, i));
                llog3.lepicipnumubxPCconns[subxI]++;
                recudp.llog3.lepicipnumubx[subxI] = String.format("r%s %s,%d", (char)h0, hostThreadSocket.getInetAddress().getCanonicalHostName(), llog3.lepicipnumubxPCconns[subxI]);
                recudp.pcforwardsocketsI[i] = subxI;
                recudp.pcforwardsockets[i] = hostThreadSocket;
                recudp.pcforwardoutputStreams[i] = recudp.pcforwardsockets[i].getOutputStream();
                hostThreadSocket = null;
                return;  // note shortcut out due to hand-over of socket to an array of outputs (so thread no longer needed)

            // connection from PC wanting to forward signals to the ESP device
            // (message from PC to ESP loop)
            } else if ((h0 == '+') && (h1 == h2) && (h1 == h3) && (h1 >= '@') && (h1 <= 'C')) {
                int subxI = h1 - '@';

                Log.i("hhanglogX pcmessage", String.format("thread %d ubxI %d", threadcount, subxI));
                SocketServerReplyThread ssrt = recudp.fosocketthread[subxI];
                if (ssrt != null) {
                    llog3.lepiccountubxPCmsgs[subxI] += 10;
                    OutputStream espoutputStream = ssrt.hostThreadSocket.getOutputStream();
                    InputStream pcinputStream = hostThreadSocket.getInputStream();
                    espoutputStream.write((String.format("Hello from Android msg relay %d\n", subxI)).getBytes());
                    byte[] buff = new byte[1000];
                    llog3.lepiccountubxPCmsgs[subxI] = 1;
                    while (true) {
                        int x = pcinputStream.read(buff);
                        if (x != -1) {
                            Log.i("hhanglogX", String.format("SocketServerReplyThread ubxI=%d relaying %d bytes", subxI, x));
                            espoutputStream.write(buff, 0, x);
                            llog3.lepiccountubxPCmsgs[subxI]++;
                        }
                        sleep(10);
                    }
                } else {
                    Log.i("hhanglogX pcmessage nohostsocket", String.format("thread %d ubxI %d", threadcount, subxI));
                    if (llog3.lepiccountubxPCmsgs[subxI] >= 0)
                        llog3.lepiccountubxPCmsgs[subxI] = -1;
                    else
                        llog3.lepiccountubxPCmsgs[subxI]--;
                }
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
    int[] ubxbytesP = {0, 0, 0, 0};
    int[] ubxbytesPD = {0, 0, 0, 0};

    long mstamp0 = 0;
    int fostreamlinesP = 0;
    int fostreamlines = 0;
    String fname = null;
    File[] fdataUBX = new File[4];
    FileOutputStream[] fostreamUBX = new FileOutputStream[4];
    SocketServerReplyThread[] fosocketthread = new SocketServerReplyThread[4];

    String ddhost = null; // set this to request socket to open
    int ddport = -1;
    Socket ddsocketup = null;
    OutputStream ddsocketupstream = null;
    int ubxIddsocketup = -1;

    int[] pcforwardsocketsI = new int[10]; // can forward to more than one socket
    Socket[] pcforwardsockets = new Socket[10];
    OutputStream[] pcforwardoutputStreams = new OutputStream[10];
    int Npcforwardsockets = 0;

    Queue<String> phonesensorqueue = null;
    String mstampsensorD0;
    LLog3 llog3 = null;

    byte[] dataE = null, dataP = null;
    int lengE = 0, lengP = 0;

    public RecUDP(Queue<String> lphonesensorqueue, String lmstampsensorD0, LLog3 lllog3) {
        phonesensorqueue = lphonesensorqueue;
        llog3 = lllog3;
        mstampsensorD0 = lmstampsensorD0;
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

    // epicfile.setText(recudp.StartDDSocket());
    public String StartDDSocket() {
        assert ddsocketup == null;
        ddport = 4006;
        ddhost = "node-red.dynamicdevices.co.uk";
        //ddhost = "144.76.167.54";
        Log.i("hhanglog78", ddhost);

        ubxIddsocketup = 3;
        for (int i = 1; i <= 3; i++) {
            if (ubxbytesP[i] != 0)
                ubxIddsocketup = i;
        }
        return String.format("uploading ubx%d to %s:%d", ubxIddsocketup, ddhost, ddport);
    }

    public String StopDDSocket() {
        ddhost = null;
        ddport = -1;

        if (ddsocketup != null) {
            try {
                ddsocketup.close();
            } catch (SocketException e) {
                Log.i("hhanglog5d", e.getMessage());
            } catch (IOException e) {
                Log.i("hhanglog6d", e.getMessage());
            }
        }
        ddsocketup = null;
        ddsocketupstream = null;
        ubxIddsocketup = -1;
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

        // create socket if required (can't be done on main thread)
        if (ubxI == ubxIddsocketup) {
            if ((ddsocketup == null) && (ddhost != null)) {
                try {
                    Log.i("hhanglog78", ddhost);
                    ddsocketup = new Socket(ddhost, ddport);
                    Log.i("hhanglog7999", ddhost);
                    ddsocketupstream = ddsocketup.getOutputStream();
                } catch (UnknownHostException e) {
                    Log.i("hhanglog7d", e.getMessage());
                    ddhost = null;
                } catch (SocketException e) {
                    Log.i("hhanglog8d", e.getMessage());
                    ddhost = null;
                } catch (IOException e) {
                    Log.i("hhanglog8d", e.getMessage());
                    ddhost = null;
                } catch (SecurityException e) {
                    Log.i("hhanglog8d", e.getMessage());
                    ddhost = null;
                }
            }
            if ((ddsocketup != null) && (ddhost == null)) {
                try {
                    ddsocketup.close();
                } catch (SocketException e) {
                    Log.i("hhanglog5d", e.getMessage());
                } catch (IOException e) {
                    Log.i("hhanglog6d", e.getMessage());
                }
                ddsocketup = null;
                ddsocketupstream = null;
                ubxIddsocketup = -1;
            }
            try {
                ddsocketupstream.write(data, 0, leng);
            } catch (IOException e) {
                Log.i("hhanglogFFp", String.valueOf(e));
            }
        }

        ubxbytesP[ubxI] += leng;
        for (int i = 0; i < leng; i++) {
            if (data[i] != 0) {   // reset to green colour only if there is a non-zero byte to warn against empty data being returned.
                ubxbytesPD[ubxI] = 0;
                break;
            }
        }

        for (int i = 0; i < Npcforwardsockets; i++) {
            Socket lpcforwardsocket = pcforwardsockets[i];
            OutputStream lpcforwardoutputStream = pcforwardoutputStreams[i];
            if ((pcforwardsocketsI[i] == ubxI) && (lpcforwardsocket != null) && (lpcforwardoutputStream != null)) {
                try {
                    lpcforwardoutputStream.write(data, 0, leng);
                } catch (IOException e) {
                    pcforwardsockets[i] = null;
                    pcforwardoutputStreams[i] = null;
                    Log.i("hhanglogX pcforward close", String.format("ubxI %d %d", ubxI, i));
                    lpcforwardsocket.close();
                }
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
                        if (llog3.lepiccountubxPCmsgs[i] == 0)
                            llog3.epicubxbytes[i].setText(String.format("UBX(%d)", ubxbytesP[i]));
                        else
                            llog3.epicubxbytes[i].setText(String.format("UBX(%d)%d", ubxbytesP[i], llog3.lepiccountubxPCmsgs[i]));
                        llog3.epicubxbytes[i].setBackgroundColor(ubxbytesPD[i] <= 3 ? 0xFFCCFFCC : 0xFFFFCCCC);
                        ubxbytesPD[i]++;
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

