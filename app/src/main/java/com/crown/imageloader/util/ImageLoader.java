package com.crown.imageloader.util;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by Crown on 2016/4/25.
 */
public class ImageLoader {

    private static ImageLoader mInstance;

    //图片缓存核心对象
    private LruCache<String, Bitmap> mLruCache;

    //线程池
    private ExecutorService mThreadPool;

    private static final int DEFAULT_THREAD_COUNT = 1;

    //队列调度方式
    private Type mType = Type.LIFO;

    //任务队列
    private LinkedList<Runnable> mTaskQueue;

    //后台轮询线程
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;

    //UI线程中的Handler 用于回调显示 image
    private Handler mUIhandler;

    private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);

    private Semaphore mSemaphoreThreadPool;

    public enum Type {
        FIFO, LIFO;
    }

    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    //初始化操作
    public void init(int threadCount, Type type) {
        //后台轮询线程
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        //线程池取出一个任务去执行
                        mThreadPool.execute(getTask());
                        try {
                            mSemaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                //释放一个信号量
                mSemaphorePoolThreadHandler.release();
                Looper.loop();
            }
        };
        mPoolThread.start();

        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap>(cacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<Runnable>();
        mType = type;

        mSemaphoreThreadPool = new Semaphore(threadCount);
    }

    /**
     * 从任务队列取出一个方法
     * @return
     */
    private Runnable getTask() {
        if(mType == Type.FIFO) {
            return mTaskQueue.removeFirst();
        }
        else if (mType == Type.LIFO) {
            mTaskQueue.removeLast();
        }
        return null;
    }

    public static ImageLoader getInstance() {
        //第一次 mInstance == null的判断提高代码效率
        if(mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    public static ImageLoader getInstance(int threadCount, Type type) {
        //第一次 mInstance == null的判断提高代码效率
        if(mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(threadCount, type);
                }
            }
        }
        return mInstance;
    }

    /**
     * 根据path为ImageView设置图片
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView) {
        imageView.setTag(path);
        if(mUIhandler == null) {
            mUIhandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                //TODO: 获取图片 imageView回调显示图片
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    Bitmap bitmap = holder.bitmap;
                    ImageView imageview = holder.imageView;
                    String path = holder.path;

                    //将path与getTag存储路径比对
                    if(imageview.getTag().toString().equals(path)) {
                        imageview.setImageBitmap(bitmap);
                    }
                }
            };
        }

        //根据path在缓存中获取bitmap
        Bitmap bitmap = getBitmapFromLruCache(path);
        if (bitmap != null) {
            refreshBitmap(bitmap, path, imageView);
        }
        else {
            addTask(new Runnable(){
                @Override
                public void run() {
                    //1.获得图片需要显示的大小
                    ImageSize imageSize = getImageViewSize(imageView);
                    //2.压缩图片
                    Bitmap bitmap = decodeSampleBitmapFromPath(path, imageSize.width,imageSize.height);
                    //3.把图片加入缓存
                    addBitmapToLruCache(path, bitmap);

                    refreshBitmap(bitmap, path, imageView);

                    mSemaphoreThreadPool.release();
                }
            });
        }
    }

    private void refreshBitmap(Bitmap bitmap, String path, ImageView imageView) {
        Message message = Message.obtain();
        ImgBeanHolder imgBeanHolder = new ImgBeanHolder();
        imgBeanHolder.bitmap = bitmap;
        imgBeanHolder.path = path;
        imgBeanHolder.imageView = imageView;
        message.obj = imgBeanHolder;
        mUIhandler.sendMessage(message);
    }

    /**
     * 将图片加入LruCache
     * @param path
     * @param bm
     */
    private void addBitmapToLruCache(String path, Bitmap bm) {
        if (getBitmapFromLruCache(path) == null) {
            if (bm != null) {
                mLruCache.put(path, bm);
            }
        }
    }

    /**
     * 根据图片需要显示的宽和高对图片进行压缩
     * @param path
     * @param width
     * @param height
     * @return
     */
    protected Bitmap decodeSampleBitmapFromPath(String path, int width, int height) {
        //获取图片的宽和高 并不把图片加载到内存中
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, width, height);

        //使用获得的inSampleSize再次解析图片
        options.inJustDecodeBounds = false; //可以把图片加载到内存了
        Bitmap bitmap = BitmapFactory.decodeFile(path,options);
        return bitmap;
    }

    /**
     * 根据需求的宽和高以及图片实际的宽和高计算SampleSize
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if(width > reqWidth || height > reqHeight) {
            int widthRadio = Math.round(width * 1.0f/reqWidth);
            int heightRadio = Math.round(height * 1.0f/reqHeight);

            inSampleSize = Math.max(widthRadio, heightRadio);
        }

        return inSampleSize;
    }

    /**
     * 根据ImageView获取适当的压缩的宽和高
     * @param imageView
     * @return
     */
    @SuppressLint("NewApi")
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();

        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();

        ViewGroup.LayoutParams lp = imageView.getLayoutParams();

        int width = imageView.getWidth();//获取imageView的实际宽度
        if(width <= 0) {
            width = lp.width; //获取imageView在layout中声明的宽度
        }
        if(width <= 0) {
//            width = imageView.getMaxWidth();//检查最大值
            width = getImageViewFieldValue(imageView, "mMaxWidth");
        }
        if (width <= 0) {
            width = displayMetrics.widthPixels;
        }


        int height = imageView.getHeight();//获取imageView的实际宽度
        if(height <= 0) {
            height = lp.height; //获取imageView在layout中声明的宽度
        }
        if(height <= 0) {
//            height = imageView.getMaxHeight();//检查最大值
            height = getImageViewFieldValue(imageView, "mMaxHeight");
        }
        if (height <= 0) {
            height = displayMetrics.heightPixels;
        }
        imageSize.width = width;
        imageSize.height = height;
        return imageSize;
    }

    /**
     * 通过反射获取imageview的某个属性值
     * @param object
     * @param fieldname
     * @return
     */
    private static int getImageViewFieldValue(Object object, String fieldname) {
        int value = 0;

        try {
            Field field = ImageView.class.getDeclaredField(fieldname);
            field.setAccessible(true);

            int fieldValue = field.getInt(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    private class ImageSize {
        int width;
        int height;
    }

    private synchronized void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);

        try {
            if (mSemaphorePoolThreadHandler == null)
                mSemaphorePoolThreadHandler.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * 根据path在缓存中获取bitmap
     * @param key
     * @return
     */
    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);
    }

    private class ImgBeanHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
