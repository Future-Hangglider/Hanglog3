package com.example.julian.hanglog3;

// something about intent indexing in the manifest I don't understand
// https://developer.android.com/studio/write/app-link-indexing#java

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Queue;
import java.util.TimeZone;

/* This file is mis-labelled.  There is no UDP in use.  It's only TCP sockets */

/* Python code for pulling in data off the phone
import socket
def loglines():
        phoneipnumber = "10.0.38.88"
        hanglogport = 9042
        addr1 = socket.getaddrinfo(phoneipnumber, hanglogport)[0][-1]
        s1 = socket.socket()
        s1.connect(addr1)
        s1.send(b"-@@@")
        rf = [ ]
        while True:
        r = s1.recv(10)
        while b"\n" in r:
        rr, r = r.split(b"\n", 1)
        rf.append(rr)
        yield b"".join(rf).decode()
        rf.clear()
        rf.append(r)
*/

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

            // connection from ESP32 with data header line AAAA, BBBB, CCCC, or @@@@
            // enters this loop
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

                // this is the receiving stream loop
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

            // connection in from PC with header -AAA, -BBB, -CCC, -@@@
            // wanting data to be forwarded (may be multiple connections)
            // socket is handed over to pcforwardsockets
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

            // connection from PC with header +AAA, +BBB, +CCC, or +@@@
            // wanting to forward signals to the ESP device (eg to flash an led)
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

                    // this is the read stream and forward loop
                    while (true) {
                        int x = pcinputStream.read(buff);
                        if (x != -1) {
                            Log.i("hhanglogX", String.format("SocketServerReplyThread ubxI=%d relaying %d bytes", subxI, x));
                            espoutputStream.write(buff, 0, x);
                            llog3.lepiccountubxPCmsgs[subxI]++;
                        }
                        sleep(10);
                    }

                // Unrecognized header instruction in socket connection
                } else {
                    Log.i("hhanglogX pcmessage nohostsocket", String.format("thread %d ubxI %d", threadcount, subxI));
                    if (llog3.lepiccountubxPCmsgs[subxI] >= 0)
                        llog3.lepiccountubxPCmsgs[subxI] = -1;
                    else
                        llog3.lepiccountubxPCmsgs[subxI]--;
                }

            // connection from PC with header -DRL (list), -DRE (erase), -DRR (read)
            // to fetch data from the files written by the app so we don't have to connect USB wires
            } else if ((h0 == '-') && (h1 == 'D') && (h2 == 'R') && ((h3 == 'L') || (h3 == 'E') || (h3 == 'R'))) {

                Log.i("hhanglogX pcmessage", String.format("thread %d -DR %c", threadcount, h3));
                //llog3.lepiccountubxPCmsgs[subxI] += 10;

                // annoying low-level code to extract a space delimeted string which will be the filename
                // An alternative would be to find a library that implements the whole ftp/http protocol, which might not be easier.
                char[] buff = new char[1001];
                int N = 0;
                buff[0] = (char)inputStream.read();
                while ((buff[0] == ' ') || (buff[0] == '\n'))
                    buff[0] = (char)inputStream.read();
                while ((buff[N] != ' ') && (buff[N] != '\n') && (buff[N] != 0) && (N < 1000))
                    buff[++N] = (char)inputStream.read();
                String fname = String.valueOf(buff, 0, N);
                Log.i("hhanglogX pcmessage", String.format("filename '%s'", fname));

                File ffiledir = new File(Environment.getExternalStorageDirectory(), fname);

                if (!fname.startsWith("hanglog")) {
                    outputStream.write(String.format("Only allowed in 'hanglog' directory\n").getBytes());
                } else if (h3 == 'L') {
                    outputStream.write((String.format("List '%s'\n", ffiledir.toString())).getBytes());
                    File[] flist = ffiledir.listFiles();
                    for (int i = 0; i < flist.length; i++)
                        outputStream.write(String.format("%s\n", flist[i].getName()).getBytes());
                    outputStream.write(String.format(".\n").getBytes());
                } else if (h3 == 'E') {
                    outputStream.write((String.format("You asked erase '%s'\n", fname)).getBytes());
                    if (ffiledir.delete())
                        outputStream.write((String.format("Erased '%s'\n", fname)).getBytes());
                    else
                        outputStream.write((String.format("Delete failed\n")).getBytes());
                } else if (h3 == 'R') {
                    long fleng = ffiledir.length();
                    Log.i("hhanglogU", String.format("sending %d bytes", fleng));
                    outputStream.write((String.format("%d\n", fleng)).getBytes());
                    FileInputStream fin = new FileInputStream(ffiledir);
                    byte[] sbuff = new byte[1000];
                    while (fleng > 0) {
                        int rn = fin.read(sbuff);
                        outputStream.write(sbuff, 0, rn);
                        fleng -= rn;
                        sleep(10);
                    }
                }

            // Unrecognized header instruction in socket connection
            } else {
                outputStream.write((String.format("Unrecognized header instructions\n")).getBytes());
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

// We could move the rest of the 4x multiple data in RecUDP to here.
// Also could use the bytesPD's trick on satellite ID (there's only 255 of them)
// to count only those that have been available for the last 15 seconds,
// so we are not substituting one satellite for another, which may cause a glitch.
class UBXstreaminfo {
    int bytesP = 0;    // count of bytes
    int bytesPD = 0;   // set to zero when non-zero byte received (used to colour the output and show non-null data is flowing)
    int N = 2048;
    char letter;
    byte[] circbuff = new byte[N];
    int Nstart = 0;
    int Nend = 0;
    int slippedubxbytes = 0;
    int ngoodsatellites = 0;
    String msgstring = "";
    StringBuilder sbmsgstring = new StringBuilder();
    int msgstringsincecount = 0;
    int nSVINFOrecs = 0;

    UBXstreaminfo(char lletter) { letter = lletter; }
    void resetubxstreaminfo() {
        bytesP = 0;
        bytesPD = 0;
        nSVINFOrecs = 0;
        ngoodsatellites = 0;
    }
    int getbval(int i) {
        return circbuff[(Nstart+i)%N]&0xFF;
    }

    void updateUBXparse(byte[] data, int leng) {
        bytesP += leng;
        for (int i = 0; i < leng; i++) {
            if (data[i] != 0) {
                bytesPD = 0;
                break;
            }
        }
        if (letter == '@')
            return;

        // append to the circular buffer
        if (Nend + leng >= N) {
            System.arraycopy(data, 0, circbuff, Nend, N - Nend);
            Nend = leng - (N - Nend);
            if (Nend != 0)
                System.arraycopy(data, leng - Nend, circbuff, 0, Nend);
            if (Nend >= Nstart) {
                Log.i("hhanglogU", "ubx circular buffer overflow for "+letter);
            }
        } else {
            System.arraycopy(data, 0, circbuff, Nend, leng);
            Nend += leng;
        }

        // seek out and parse the next UBX record
        while (true) {
            int Nbytes = (Nstart <= Nend ? Nend - Nstart : N - Nstart + Nend);
            if (Nbytes < 10)
                break;
            if (!((getbval(0) == 0xB5) && (getbval(1) == 0x62))) {
                slippedubxbytes++;
                Nstart = (Nstart + 1)%N;
                continue;
            }
            int payloadlength = getbval(4) + getbval(5)*256;
            if (payloadlength > N - 10) {
                Log.i("hhanglogU", "ubx payload "+payloadlength+" too long, advancing for "+letter);
                Nstart = (Nstart + 1)%N;
                continue;
            }
            if (Nbytes < payloadlength + 8)
                break;

            if (slippedubxbytes != 0) {
                Log.i("hhanglogU", "ubx circular buffer slipped "+slippedubxbytes+" bytes");
                slippedubxbytes = 0;
            }

            // parse UBX-NAV-SVINFO to count good satellites
            if ((getbval(2) == 0x01) && (getbval(3) == 0x30)) {
                int lngoodsatellites = 0;
                for (int i = 11; i < payloadlength; i += 12) {
                    if ((getbval(6+i-3) != 0xFF) && ((getbval(6+i) & 0x0F) >= 5))
                        lngoodsatellites++;
                }
                ngoodsatellites = lngoodsatellites;
                nSVINFOrecs++;
                Log.i("hhanglogU", "ubx ngoodsatellites "+ngoodsatellites);

            // parse UBX-msg to get battery output
            } else if ((getbval(2) == 0x21) && (getbval(3) == 0x04)) {
                sbmsgstring.setLength(0);
                for (int i = 0; i < payloadlength; i++)
                    sbmsgstring.append((char)getbval(6+i));
                msgstring = sbmsgstring.toString();
                msgstringsincecount = 0;
                Log.i("hhanglogU", "ubx msgstring "+msgstring);
            }


            Nstart = (Nstart + payloadlength + 8)%N;
        }
    }
}

class RecUDP extends Service {
    UBXstreaminfo[] ubxstreaminfos = { new UBXstreaminfo('@'),
                                       new UBXstreaminfo('A'),
                                       new UBXstreaminfo('B'),
                                       new UBXstreaminfo('C') };

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

        SimpleDateFormat ddsdf = new SimpleDateFormat("'dd_'yyyy_MM_dd_HHmmss");
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
        ubxstreaminfos[0].resetubxstreaminfo();
        ubxstreaminfos[1].resetubxstreaminfo();
        ubxstreaminfos[2].resetubxstreaminfo();
        ubxstreaminfos[3].resetubxstreaminfo();

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

        ubxIddsocketup = 0;
        for (int i = 1; i <= 3; i++) {
            if (ubxstreaminfos[i].bytesP != 0)
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

    public void forwardtoDDSocket(byte[] data, int leng)
    {
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
        if (ddsocketupstream != null) {
            try {
                ddsocketupstream.write(data, 0, leng);
            } catch (IOException e) {
                Log.i("hhanglogFFp", String.valueOf(e));
            }
        }
    }

    public void forwardtoPCsockets(int ubxI, byte[] data, int leng) throws IOException
    {
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

    // this is where we separate out multiple UBX streams from different ipnumbers
    public void writefostreamUBX(int ubxI, byte[] data, int leng) throws IOException {
        try {
        if ((fdataUBX[ubxI] != null) && (fostreamUBX[ubxI] == null) && (ubxstreaminfos[ubxI].bytesP == 0))
            fostreamUBX[ubxI] = new FileOutputStream(fdataUBX[ubxI]);
        } catch (FileNotFoundException e) {
            Log.i("hhanglogFFu", String.valueOf(e));
        }
        FileOutputStream lfostreamUBX = fostreamUBX[ubxI];
        if (lfostreamUBX != null) {
            lfostreamUBX.write(data, 0, leng);
            lfostreamUBX.flush();
        }

        // create socket if required (can't be done on main thread)
        if (ubxI == ubxIddsocketup)
            forwardtoDDSocket(data, leng);

        ubxstreaminfos[ubxI].updateUBXparse(data, leng);

        forwardtoPCsockets(ubxI, data, leng);
    }

    public void writefostream(byte[] data, int leng) throws IOException {
        FileOutputStream lfostream = fostreamUBX[0]; // protect null pointers from thread conditions
        if (lfostream != null)
            lfostream.write(data, 0, leng);

        if (0 == ubxIddsocketup)
            forwardtoDDSocket(data, leng);
        forwardtoPCsockets(0, data, leng);

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

                        String spc = (llog3.lepiccountubxPCmsgs[i] != 0 ? String.valueOf(llog3.lepiccountubxPCmsgs[i]) : "");
                        if (ubxstreaminfos[i].msgstring.length() > 9) {// battvolt=%4.2fv
                            spc = ubxstreaminfos[i].msgstring.substring(9);
                            if (ubxstreaminfos[i].msgstringsincecount++ > 90)
                                ubxstreaminfos[i].msgstring = "";
                        }
                        llog3.epicubxbytes[i].setText(String.format("UBX(%d#%d)%s", ubxstreaminfos[i].nSVINFOrecs, ubxstreaminfos[i].ngoodsatellites, spc));
                        llog3.epicubxbytes[i].setBackgroundColor(ubxstreaminfos[i].bytesPD <= 3 ? 0xFFCCFFCC : 0xFFFFCCCC);
                        ubxstreaminfos[i].bytesPD++;
                    }
                }
            });
            mstamp0 = mstamp + 250;
        }
    }

    // this gets to the threading object which sits on socket.receive
    public String msgtosend = null;

    //@Override
    public void trun(Thread t) {
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
                    t.sleep(200);
                } catch (InterruptedException e) {
                    Log.i("hhanglogI", e.getMessage());
                }
            }
        }
    }

    Thread thr = null;
    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        Log.i("hhanglog5","UDP onstartcommand");
        thr = new Thread("MyService(" + startId + ")") {
            @Override
            public void run() {
                trun(thr);
                stopSelf();
            }
        };
        thr.start();
        return 0;
    }

    private final IBinder mBinder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.i("hhanglog5","UDP onbind");
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.i("hhanglog5","UDP oncreate");

        // The service is being created
    }
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i("hhanglog5","UDP onunbind");
        // All clients have unbound with unbindService()
        return false;
    }
    @Override
    public void onRebind(Intent intent) {
        Log.i("hhanglog5","UDP onrebind");
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }
    @Override
    public void onDestroy() {
        Log.i("hhanglog5","UDP ondestroy");
        // The service is no longer used and is being destroyed
    }

}

