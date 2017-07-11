package com.lizhy.screenshot_library.observer;


import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.lizhy.screenshot_library.R;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ${ScreenshotObserver}
 * Created by spark_lizhy on 2017/6/11.
 */
public class ScreenshotObserver extends ContentObserver {

    private static final String[] KEYWORDS = {
            "screenshot", "screen_shot", "screen-shot", "screen shot",
            "screencapture", "screen_capture", "screen-capture", "screen capture",
            "screencap", "screen_cap", "screen-cap", "screen cap"
    };
    private static Context mContext;

    private long lastId;
    private static ScreenshotObserver mInternalObserver;
    private static ScreenshotObserver mExternalObserver;
    private String mFilePath;
    private Bitmap mScreenshotBitmap;
    private final Resources mResources;

    private final int m40;

    public static void register(Context context) {
        startObserve(context);
    }

    public static void unRegister(){
        stopObserve();
    }

    private ScreenshotObserver(Context context) {
        super(null);
        mContext = context;
        mResources = mContext.getResources();
        m40 = 40;
    }

    private static void startObserve(Context context) {
        if (mExternalObserver == null) {
            mExternalObserver = new ScreenshotObserver(context);
        }
        if (mInternalObserver == null) {
            mInternalObserver = new ScreenshotObserver(context);
        }
        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                false,
                mExternalObserver);
        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                false,
                mExternalObserver);
    }

    private static void stopObserve() {
        mContext.getContentResolver().unregisterContentObserver(mInternalObserver);
        mContext.getContentResolver().unregisterContentObserver(mExternalObserver);
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);

        String[] columns = {
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns._ID,
        };
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    columns,
                    null,
                    null,
                    MediaStore.MediaColumns.DATE_ADDED + " desc");
            if (cursor == null) {
                return;
            }
            if (!cursor.moveToFirst()) {
                return;
            }

            mFilePath = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
            long addTime = cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED));
            long id = cursor.getLong(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            if (checkTime(addTime)
                    && checkPath(mFilePath)
                    && checkSize(mFilePath)
                    && !checkId(id)) {
                lastId = id;

                if (!isApplicationInBackground(mContext)) {
                    if (mFilePath != null) {
                        do {
                            mScreenshotBitmap = BitmapFactory.decodeFile(mFilePath);
                        } while (mScreenshotBitmap == null);
                    }
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public boolean deliverSelfNotifications() {
        return super.deliverSelfNotifications();
    }

    private boolean checkTime(long addTime) {
        return System.currentTimeMillis() - addTime * 1000 <= 1500;
    }

    private boolean checkId(long id) {
        return (lastId == id);
    }


    private boolean checkSize(String filePath) {
        DisplayMetrics metrics = getHasVirtualKey();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        return metrics.widthPixels >= options.outWidth && metrics.heightPixels >= options.outHeight;
    }


    private boolean checkPath(String filePath) {
        filePath = filePath.toLowerCase();
        for (String keyWork : KEYWORDS) {
            if (filePath.contains(keyWork)) {
                return true;
            }
        }
        return false;
    }


    private DisplayMetrics getHasVirtualKey() {

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = mWindowManager.getDefaultDisplay();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        @SuppressWarnings("rawtypes")
        Class c;
        try {
            c = Class.forName("android.view.Display");
            @SuppressWarnings("unchecked")
            Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
            method.invoke(display, metrics);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return metrics;
    }


    /**
     * 传入待合成的Bitmap资源，例如想要拼接二维码则传入你的二维码Bitmap
     *
     * @param bitmapResource 待拼接的BitmaResource
     * @return 返回屏幕截图与Bitmap的合成图
     */
    public Bitmap shareShot(int bitmapResource) {
        Bitmap qrCodeBitmap =
                BitmapFactory.decodeResource(mResources, bitmapResource);

        //让二维码居中
        int x = (mScreenshotBitmap.getWidth() - qrCodeBitmap.getWidth()) / 2;

        int newImageH = mScreenshotBitmap.getHeight() + qrCodeBitmap.getHeight() + 2 * m40;
        mScreenshotBitmap = mergeBitmap(newImageH, mScreenshotBitmap.getWidth(),
                qrCodeBitmap, x, mScreenshotBitmap.getHeight() + m40,
                mScreenshotBitmap, 0, 0);

        return mScreenshotBitmap;
    }

    /**
     * 传入待合成的Bitmap，例如想要拼接二维码则传入你的二维码Bitmap
     *
     * @param bitmap 待拼接的Bitmap
     * @return 返回屏幕截图与Bitmap的合成图
     */
    public Bitmap shareShot(Bitmap bitmap) {

        //让二维码居中
        int x = (mScreenshotBitmap.getWidth() - bitmap.getWidth()) / 2;

        int newImageH = mScreenshotBitmap.getHeight() + bitmap.getHeight() + 2 * m40;
        mScreenshotBitmap = mergeBitmap(newImageH, mScreenshotBitmap.getWidth(),
                bitmap, x, mScreenshotBitmap.getHeight() + m40,
                mScreenshotBitmap, 0, 0);

        return mScreenshotBitmap;
    }


    private Bitmap mergeBitmap(int newImageH, int newImageW,
                               Bitmap newBitmap, float newX, float newY,
                               Bitmap oldBitmap, float oldX, float oldY) {
        if (null == newBitmap || null == oldBitmap) {
            return null;
        }

        Bitmap newbmp = Bitmap.createBitmap(newImageW, newImageH,
                Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(newbmp);
        Paint paint = new Paint();//绘制截图与二维码之间的分隔线
        paint.setColor(ContextCompat.getColor(mContext, R.color.color_dfdfdf));

        cv.drawColor(ContextCompat.getColor(mContext, R.color.color_ffffff));
        cv.drawBitmap(oldBitmap, oldX, oldY, null);
        cv.drawLine(0, newY - (m40 / 2), newImageW, newY - (m40 / 2) - 2, paint);
        cv.drawBitmap(newBitmap, newX, newY, null);
        cv.save(Canvas.ALL_SAVE_FLAG);// 保存
        cv.restore();// 存储

        return newbmp;
    }

    private static boolean isApplicationInBackground(Context context) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningTaskInfo> taskList = am.getRunningTasks(1);
        if (taskList != null && !taskList.isEmpty()) {
            ComponentName topActivity = taskList.get(0).topActivity;
            if (topActivity != null && !topActivity.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}