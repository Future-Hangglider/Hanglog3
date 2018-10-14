package com.example.julian.hanglog3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.ImageView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphPlot {
    int vwidth = 0;
    int vheight = 0;
    Bitmap mBitmap;
    Canvas tiltycanvas;
    Paint mPaint = new Paint();
    int mColorBackground;
    float ang = 0.0F;

    double northorient = 0.0, pitch = 0.0, roll = 0.0;

    Pattern pZ = Pattern.compile("^Zt[0-9A-F]{8}x[0-9A-F]{4}y[0-9A-F]{4}z[0-9A-F]{4}a[0-9A-F]{4}b[0-9A-F]{4}c[0-9A-F]{4}w([0-9A-F]{4})x([0-9A-F]{4})y([0-9A-F]{4})z([0-9A-F]{4})");

    GraphPlot(Context context, ImageView tiltyview) {
        vwidth = tiltyview.getWidth();
        vheight = tiltyview.getHeight();

        mBitmap = Bitmap.createBitmap(vwidth, vheight, Bitmap.Config.ARGB_8888);
        tiltyview.setImageBitmap(mBitmap);
        tiltycanvas = new Canvas(mBitmap);
        mPaint.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        mPaint.setStrokeWidth(5);
        mColorBackground = ContextCompat.getColor(context, R.color.colorPrimaryDark);
        Log.i("hhanglogE", "COLO"+vwidth+" "+vheight+"  "+mColorBackground);
    }


    public double s16(String d) {
        int j = Integer.valueOf(d, 16);
        return (j < 32768 ? j : j - 65536);
    }
    public void drawstuff(String dstring) {
        Matcher m = pZ.matcher(dstring);
        if (m.find()) {
            double q0 = s16(m.group(1));
            double q1 = s16(m.group(2));
            double q2 = s16(m.group(3));
            double q3 = s16(m.group(4));
            double riqsq = q0*q0 + q1*q1 + q2*q2 + q3*q3;
            double iqsq = 1.0/riqsq;
            double r02 = q0*q2*2 * iqsq, r13 = q1*q3*2 * iqsq;
            double sinpitch = r13 - r02;
            double r01 = q0*q1*2 * iqsq, r23 = q2*q3*2 * iqsq;
            double sinroll = r23 + r01;
            double r00 = q0*q0*2 * iqsq, r11 = q1*q1*2 * iqsq, r03 = q0*q3*2 * iqsq, r12 = q1*q2*2 * iqsq;
            double a00 = r00 - 1 + r11, a01 = r12 + r03;
            double rads = Math.atan2(a00, -a01);

            northorient = 180 - Math.toDegrees(rads);
            pitch = Math.toDegrees(Math.asin(sinpitch));
            roll = Math.toDegrees(Math.asin(sinroll));
            Log.i("hhanglogM", "pitch "+pitch+" roll "+roll);

        }
        tiltycanvas.drawColor(mColorBackground);
        ang += 0.1;
        float fx = (float)(Math.cos(Math.toRadians(northorient))*0.4+0.5);
        float fy = (float)(-Math.sin(Math.toRadians(northorient))*0.4+0.5);
        float hline = (float)(vheight*0.5 - roll);
        float vline = (float)(vwidth*0.5 + pitch*2);
        tiltycanvas.drawLine(vwidth*0.5F, vheight*0.5F, vwidth*fx, vheight*fy, mPaint);
        tiltycanvas.drawLine(0, hline, vwidth, hline, mPaint);
        tiltycanvas.drawLine(vline, 0, vline, vheight, mPaint);
        //Log.i("hhanglogM", "x"+Integer.valueOf(m.group(2), 16)+" y"+Integer.valueOf(m.group(3), 16)+" z"+Integer.valueOf(m.group(4), 16));
    }
}


