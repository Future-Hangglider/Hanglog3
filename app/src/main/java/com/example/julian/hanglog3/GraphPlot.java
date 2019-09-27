package com.example.julian.hanglog3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.ImageView;


public class GraphPlot {
    int Nmaxneckpoints = 100;
    int vwidth = 0;
    int vheight = 0;
    Bitmap mBitmap;
    Canvas tiltycanvas;
    int mColorBackground;
    Paint mPaint = new Paint(), mPaint2 = new Paint(), mPaintText = new Paint();
    CurrentPos cpos;

    int neckrangemode = 0;
    int Nneckpoints = 0;
    float northorient0 = 0;
    float[] neckpointsList = new float[Nmaxneckpoints*4];

    double neckshakemin = 0;
    double neckshakemax = 0;
    double necknodmin = 0;
    double necknodmax = 0;

    GraphPlot(Context context, ImageView tiltyview, CurrentPos lcpos) {
        cpos = lcpos;
        vwidth = tiltyview.getWidth();
        vheight = tiltyview.getHeight();

        mBitmap = Bitmap.createBitmap(vwidth, vheight, Bitmap.Config.ARGB_8888);
        tiltyview.setImageBitmap(mBitmap);
        tiltycanvas = new Canvas(mBitmap);
        mColorBackground = ContextCompat.getColor(context, R.color.colorPrimaryDark);
        mPaint.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        mPaint.setStrokeWidth(5);
        mPaint2.setColor(ContextCompat.getColor(context, R.color.colorAccent2));
        mPaint2.setStrokeWidth(5);
        mPaint2.setTextSize(40);
        mPaintText.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        mPaintText.setTextSize(70);
        Log.i("hhanglogE", "COLO"+vwidth+" "+vheight+"  "+mColorBackground);
    }

    void SetNeckRangeMode(boolean bneckrangeonswitch) {
        if (bneckrangeonswitch) {
            Nneckpoints = 0;
            neckrangemode = 1;
            northorient0 = (float) cpos.northorientA;
        } else {
            neckrangemode = 2;
        }
    }

    public void draworient(double northorient, double pitch, double roll, Paint paint) {
        float fx = (float)(Math.sin(Math.toRadians(northorient))*0.4+0.5);
        float fy = (float)(Math.cos(Math.toRadians(northorient))*0.4+0.5);
        float hline = (float)(vheight*0.5 + roll*2);
        float vline = (float)(vwidth*0.5 + pitch*4);
        tiltycanvas.drawLine(vwidth*0.5F, vheight*0.5F, vwidth*fx, vheight*fy, paint);
        tiltycanvas.drawLine(0, hline, vwidth, hline, paint);
        tiltycanvas.drawLine(vline, 0, vline, vheight, paint);
    }

    public void drawstuffhanglog() {
        tiltycanvas.drawColor(mColorBackground);

        Bitmap cameraview = cpos.cameraview;
        if (cameraview != null)
            tiltycanvas.drawBitmap(cameraview, null, new Rect(0, 0, vwidth, vheight), null);

        tiltycanvas.drawText(String.format("gps: %.3f,%.3f", cpos.lat0, cpos.lng0), 0, 50, mPaintText);
        tiltycanvas.drawText(String.format("D: %.1f,%.1f", cpos.xpos, cpos.ypos), 0, 130, mPaintText);
        tiltycanvas.drawText(String.format("Ph: %.1f,%.1f", cpos.xposA, cpos.yposA), 0, 210, mPaintText);
        draworient(cpos.northorient, cpos.pitch, cpos.roll, mPaint2);
        draworient(cpos.northorientA, cpos.pitchA, cpos.rollA, mPaint);
        if (cpos.orientcalibration != -1)
            tiltycanvas.drawText(String.format("calib: %d%d%d%d", ((cpos.orientcalibration>>6) & 3), ((cpos.orientcalibration>>4) & 3), ((cpos.orientcalibration>>2) & 3), ((cpos.orientcalibration>>0) & 3)), tiltycanvas.getWidth()-200, tiltycanvas.getHeight()-20, mPaint2);
    }

    public void drawstuffneckrange(double neckshake, double necknod) {
        tiltycanvas.drawColor(mColorBackground);
        tiltycanvas.drawText(String.format("neckshake: %.2f", neckshake), 10, 60, mPaintText);
        tiltycanvas.drawText(String.format("necknod: %.2f", necknod), 10, 135, mPaintText);
        tiltycanvas.drawText(String.format("n:%d", Nneckpoints), tiltycanvas.getWidth() - 150, 60, mPaintText);
        tiltycanvas.drawText(String.format("shake range: %.0f %.0f", neckshakemin, neckshakemax), 10, tiltycanvas.getHeight()-60, mPaint2);
        tiltycanvas.drawText(String.format("nod range: %.0f %.0f", necknodmin, necknodmax), 10, tiltycanvas.getHeight()-20, mPaint2);

        tiltycanvas.drawLines(neckpointsList, 0, Nneckpoints*4, mPaint2);
    }
    public void addpointneckrange(double neckshake, double necknod) {
        // lines are sequence of segments which need to be
        neckpointsList[Nneckpoints*4] = (float)(0.5*tiltycanvas.getWidth());
        neckpointsList[Nneckpoints*4 + 1] = (float)(0.5*tiltycanvas.getHeight());
        //neckpointsList[Nneckpoints*4] = (Nneckpoints != 0 ? neckpointsList[Nneckpoints*4-2] : 0.0F);
        //neckpointsList[Nneckpoints*4 + 1] = (Nneckpoints != 0 ? neckpointsList[Nneckpoints*4-1] : 0.0F);
        neckpointsList[Nneckpoints*4 + 2] = (float)((necknod/2.0/90.0 + 0.5)*tiltycanvas.getWidth());
        neckpointsList[Nneckpoints*4 + 3] = (float)((neckshake/2.0/90.0 + 0.5)*tiltycanvas.getHeight());

        if ((Nneckpoints == 0) || (neckshake < neckshakemin))
            neckshakemin =  neckshake;
        if ((Nneckpoints == 0) || (neckshake > neckshakemax))
            neckshakemax =  neckshake;
        if ((Nneckpoints == 0) || (necknod < necknodmin))
            necknodmin =  necknod;
        if ((Nneckpoints == 0) || (necknod > necknodmax))
            necknodmax =  necknod;

        Nneckpoints++;
    }


    // getting here about every 250ms
    public void drawstuff() {
        if (neckrangemode == 0)
            drawstuffhanglog();
        else {
            double neckshake = (((cpos.northorientA - northorient0)+360+180)%360)-180;
            double necknod = cpos.pitchA;
            if (neckrangemode == 1) {
                if (Nneckpoints < Nmaxneckpoints) {
                    addpointneckrange(neckshake, necknod);
                }
            }
            drawstuffneckrange(neckshake, necknod);
        }

    }
}


