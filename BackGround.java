package com.example.android.screencapture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;

import com.example.android.common.logger.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;


public class BackGround extends Service {
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final String TAG = " BackGround: ";
    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private int mScreenDensity;

    private int mResultCode;
    private Intent mResultData;

    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButtonToggle;
    private SurfaceView mSurfaceView;
    private ImageReader mImageReader;
    @Override
    public void onCreate() {
        super.onCreate();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(input)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
        //do heavy work on a background thread


        setUpMediaProjection();
        setupVirtualDisplay();




        for(int i = 0; i < 20; i++) {
            try {
                Log.i(TAG, "Foreground Service");
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }


        //stopSelf();
        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenCapture();
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void startScreenCapture() {
        BackGround activity = BackGround.this;
        if (mSurface == null || activity == null) {
            return;
        }
        if (mMediaProjection != null) {
            setupVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setupVirtualDisplay();
        }
    }
    private void setupVirtualDisplay() {
        int height = Resources.getSystem().getDisplayMetrics().widthPixels;
        int width = Resources.getSystem().getDisplayMetrics().heightPixels;
        mImageReader = ImageReader.newInstance(width,
                height, 0x1, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "ScreenCapture", width, height,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
        mImageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Log.i(TAG, "in OnImageAvailable");
                        FileOutputStream fos = null;
                        Bitmap bitmap = null;
                        Image img = null;
                        try {
                            img = reader.acquireLatestImage();
                            if (img != null) {
                                Image.Plane[] planes = img.getPlanes();
                                if (planes[0].getBuffer() == null) {
                                    return;
                                }
                                int width = img.getWidth();
                                int height = img.getHeight();
                                int pixelStride = planes[0].getPixelStride();
                                int rowStride = planes[0].getRowStride();
                                int rowPadding = rowStride - pixelStride * width;
                                byte[] newData = new byte[width * height * 4];

                                int offset = 0;
                                DisplayMetrics metrics = null;
                                bitmap = Bitmap.createBitmap(metrics,width, height, Bitmap.Config.ARGB_8888);
                                ByteBuffer buffer = planes[0].getBuffer();
                                for (int i = 0; i < height; ++i) {
                                    for (int j = 0; j < width; ++j) {
                                        int pixel = 0;
                                        pixel |= (buffer.get(offset) & 0xff) << 16;     // R
                                        pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
                                        pixel |= (buffer.get(offset + 2) & 0xff);       // B
                                        pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
                                        bitmap.setPixel(j, i, pixel);
                                        offset += pixelStride;
                                    }
                                    offset += rowPadding;
                                }

                                calculateFingerPrint(bitmap);
                                img.close();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (null != fos) {
                                try {
                                    fos.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (null != bitmap) {
                                bitmap.recycle();
                            }
                            if (null != img) {
                                img.close();
                            }

                        }
                    }
                }, null);
        mButtonToggle.setText(R.string.stop);
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }



// ---------------------------------------------------------------------------------------------->
// ALL CODE UNDER THIS IS IRRELEVANT

    private static void calculateFingerPrint(Bitmap bitmap) {
        float scale_width, scale_height;


        scale_width = 8.0f / bitmap.getWidth();
        scale_height = 8.0f / bitmap.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(scale_width, scale_height);

        Bitmap scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        getFingerPrint(scaledBitmap);

        bitmap.recycle();
        scaledBitmap.recycle();
    }

    private static long getFingerPrint(Bitmap bitmap) {
        double[][] grayPixels = getGrayPixels(bitmap);
        double grayAvg = getGrayAvg(grayPixels);
        return getFingerPrint1(grayPixels, grayAvg);
    }

    private static long getFingerPrint1(double[][] pixels, double avg) {
        int width = pixels[0].length;
        int height = pixels.length;

        byte[] bytes = new byte[height * width];

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (pixels[i][j] >= avg) {
                    bytes[i * height + j] = 1;
                    stringBuilder.append("1");
                } else {
                    bytes[i * height + j] = 0;
                    stringBuilder.append("0");
                }
            }
        }

        Log.i(TAG, "getFingerPrint: " + stringBuilder.toString());

        long fingerprint1 = 0;
        long fingerprint2 = 0;
        for (int i = 0; i < 64; i++) {
            if (i < 32) {
                fingerprint1 += (bytes[63 - i] << i);
            } else {
                fingerprint2 += (bytes[63 - i] << (i - 31));
            }
        }

        return (fingerprint2 << 32) + fingerprint1;
    }

    private static double getGrayAvg(double[][] pixels) {
        int width = pixels[0].length;
        int height = pixels.length;
        int count = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                count += pixels[i][j];
            }
        }
        return count / (width * height);
    }


    private static double[][] getGrayPixels(Bitmap bitmap) {
        int width = 8;
        int height = 8;
        double[][] pixels = new double[height][width];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                pixels[i][j] = computeGrayValue(bitmap.getPixel(i, j));
            }
        }
        return pixels;
    }

    private static double computeGrayValue(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = (pixel) & 255;
        return 0.3 * red + 0.59 * green + 0.11 * blue;
    }
}