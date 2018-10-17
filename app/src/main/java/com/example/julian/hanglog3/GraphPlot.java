package com.example.julian.hanglog3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.ImageView;

public class GraphPlot {
    int vwidth = 0;
    int vheight = 0;
    Bitmap mBitmap;
    Canvas tiltycanvas;
    int mColorBackground;
    Paint mPaint = new Paint(), mPaint2 = new Paint(), mPaintText = new Paint();
    CurrentPos cpos;

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
        mPaintText.setColor(ContextCompat.getColor(context, R.color.colorPrimary));
        mPaintText.setTextSize(70);
        Log.i("hhanglogE", "COLO"+vwidth+" "+vheight+"  "+mColorBackground);
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

    public void drawstuff() {
        tiltycanvas.drawColor(mColorBackground);
        tiltycanvas.drawText(String.format("gps: %.3f,%.3f", cpos.lat0, cpos.lng0), 0, 50, mPaintText);
        tiltycanvas.drawText(String.format("D: %.1f,%.1f", cpos.xpos, cpos.ypos), 0, 130, mPaintText);
        tiltycanvas.drawText(String.format("Ph: %.1f,%.1f", cpos.xposA, cpos.yposA), 0, 210, mPaintText);
        draworient(cpos.northorient, cpos.pitch, cpos.roll, mPaint2);
        draworient(cpos.northorientA, cpos.pitchA, cpos.rollA, mPaint);
    }
}


