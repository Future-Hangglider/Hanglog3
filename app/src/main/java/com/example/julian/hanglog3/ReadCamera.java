package com.example.julian.hanglog3;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class ReadCamera extends Thread {

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    ImageReader imageReader = null;
    LLog3 llog3;

    ReadCamera (LLog3 lllog3) {
        llog3 = lllog3;
    }


    protected CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d("hhanglogC5", "CameraDevice.StateCallback onOpened");
            cameraDevice = camera;
            Log.d("hhanglogC55", imageReader.getSurface().toString());

            List<Surface> x = Arrays.asList(imageReader.getSurface());
            for (Surface sx : x)
                Log.d("hhanglogC55h", sx.toString());

            try {
                cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, null);
            } catch (CameraAccessException e){
                Log.e("hhanglogC5", e.getMessage());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d("hhanglogC5", "CameraDevice.StateCallback onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d("hhanglogC5", "CameraDevice.StateCallback onError " + error);
        }
    };

    protected CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession lsession) {
            Log.i("hhanglogC6", "CameraCaptureSession.StateCallback onConfigured");
            session = lsession;
            try {
                lsession.setRepeatingRequest(createCaptureRequest(), null, null);
            } catch (CameraAccessException e) {
                Log.e("hhanglogC6", e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.i("hhanglogC6", "CameraCaptureSession.StateCallback congfigfailed");
            Log.i("hhanglogC6", session.toString());
        }
    };

    protected CaptureRequest createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(imageReader.getSurface());
            return builder.build();
        } catch (CameraAccessException e) {
            Log.e("hhanglogC6", e.getMessage());
            return null;
        }
    }

    protected ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Log.i(TAG, "onImageAvailable");
            Image img = reader.acquireLatestImage();
            if (img != null) {
                Image.Plane[] planes = img.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                Log.d("hhanglogC7", "buffer thing "+buffer.capacity());

                byte[] data = new byte[buffer.capacity()];
                buffer.get(data);

                File fdir = new File(Environment.getExternalStorageDirectory(), "hanglog");
                TimeZone timeZoneUTC = TimeZone.getTimeZone("UTC");
                Calendar rightNow = Calendar.getInstance(timeZoneUTC);
                SimpleDateFormat ddsdf = new SimpleDateFormat("yyyyMMddHHmmss");
                ddsdf.setTimeZone(timeZoneUTC);
                File filejpg = new File(fdir, ddsdf.format(rightNow.getTime()) + ".jpg");
                try {
                    FileOutputStream fos = new FileOutputStream(filejpg);
                    fos.write(data);
                    fos.close();
                    Log.i("hhanglogP", "Pic saved " + filejpg.toString());
                } catch (IOException e) {
                    Log.i("hhanglogP", String.valueOf(e));
                }



                img.close();
            }
        }
    };

    public void gologpics(boolean isChecked, Context context) {
        Log.d("hhanglogC", "Camera isopennn");
        CameraManager cameraManager = (CameraManager)llog3.getSystemService(Context.CAMERA_SERVICE);
        Log.d("hhanglogC", "Camera isopen");
        String cameraID = null;
        CameraCharacteristics characteristics = null;
        try {
            String[] cameraIDlist = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraIDlist.length; i++) {
                String lcameraID = cameraIDlist[i];
                CameraCharacteristics lcharacteristics = cameraManager.getCameraCharacteristics(lcameraID );
                int cOrientation = lcharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.d("hhanglogC2", i + " front " + lcameraID);
                    cameraID = lcameraID;
                    characteristics = lcharacteristics;
                    break;
                }
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }

        if (cameraID == null)
            return;

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        assert map != null;
        Size[] imageDimensionlist = map.getOutputSizes(SurfaceTexture.class);
        for (int i = 0; i < imageDimensionlist.length; i++)
            Log.d("hhanglogC3", imageDimensionlist[i].toString());
        Size imageDimension = imageDimensionlist[0];
        Log.d("hhanglogC3", imageDimension.toString());

        // Add permission for camera and let user grant the permission
        if (ActivityCompat.checkSelfPermission(llog3, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(llog3, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
            return;
        }

        //imageReader = ImageReader.newInstance(320, 240, 0x00000001 /*ImageFormat.YUV_420_888*/, 2 /* images buffered */);
        imageReader = ImageReader.newInstance(320, 240, ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
        Log.d("hhanglogC33", imageReader.getSurface().toString());
        try {
            cameraManager.openCamera(cameraID, cameraStateCallback, null);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }

        Log.d("hhanglogC3", "imagereadercreated");



/*
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(AndroidCameraApi.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
*/

/*        Camera camera = null;
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
                Log.d("hhanglogP", "Camera found");
                try {
                    camera = Camera.open(i);
                }  catch (RuntimeException e) {
                    Log.d("hhanglogP", "Camera permissions bad "+e.toString());
                }
                break;
            }
        }
        if (camera != null)
            camera.takePicture(null, null, new PhotoHandler(context));
*/
    }


}
