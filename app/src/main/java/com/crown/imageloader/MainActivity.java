package com.crown.imageloader;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crown.imageloader.bean.FolderBean;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//import com.crown.imageloader.util.ImageLoader;

//import com.crown.imageloader.util.ImageLoader;

public class MainActivity extends AppCompatActivity {

    private GridView mGridView;
    private List<String> mImgs;
    private ImageAdapter mImageAdapter;

    private RelativeLayout mBottomLy;
    private TextView mDirName;
    private TextView mDirCount;

    private File mCurrentDir;
    private int mMaxCount;

    private List<FolderBean> mFolderBeans = new ArrayList<FolderBean>();

    ProgressDialog mProgressDialog;

    private static final int DATA_LOADED = 0x110;

    private ListImageDirPopupWindow mDirPopupWindow;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == DATA_LOADED) {
                mProgressDialog.dismiss();
                dataToView();
                initDirPopupWindow();
            }
        }
    };

    private void initDirPopupWindow() {
        mDirPopupWindow = new ListImageDirPopupWindow(this, mFolderBeans);
        mDirPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                lightOn();
            }
        });

        mDirPopupWindow.setOnDirSelectedListener(new ListImageDirPopupWindow.OnDirSelectedListener() {
            @Override
            public void onSelected(FolderBean folderBean) {
                mCurrentDir = new File(folderBean.getDir());
                mImgs = Arrays.asList(mCurrentDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        if (filename.endsWith(".jpg")
                                || filename.endsWith(".jpeg")
                                || filename.endsWith(".png"))
                            return true;
                        return false;
                    }
                }));
                mImageAdapter = new ImageAdapter(MainActivity.this, mImgs, mCurrentDir.getAbsolutePath());
                mGridView.setAdapter(mImageAdapter);

                mDirCount.setText(mImgs.size() + "");
                mDirName.setText(folderBean.getName());

                mDirPopupWindow.dismiss();
            }
        });
    }

    /**
     * 内容区域变亮
     */
    private void lightOn() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.alpha = 1.0f;
        getWindow().setAttributes(lp);
    }

    private void dataToView() {
        if(mCurrentDir == null) {
            Toast.makeText(this, "未扫描到任何图片", Toast.LENGTH_SHORT).show();
            return;
        }
        mImgs = Arrays.asList(mCurrentDir.list());

        mImageAdapter =  new ImageAdapter(this, mImgs, mCurrentDir.getAbsolutePath());
        mGridView.setAdapter(mImageAdapter);
        mDirCount.setText(mMaxCount + "");
        mDirName.setText(mCurrentDir.getName());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initDatas();
        initEvent();
    }

    private void initEvent() {
        mBottomLy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                mDirPopupWindow.setAnimationStyle();
                mDirPopupWindow.setAnimationStyle(R.style.dir_popupwindow_anim);
                mDirPopupWindow.showAsDropDown(mBottomLy, 0, 0);

                lightOff();
            }
        });
    }

    //内容区域变暗
    private void lightOff() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.alpha = .3f;
        getWindow().setAttributes(lp);
    }

    /**
     * 利用content provider扫描手机中的所有图片
     */
    private void initDatas() {
        if(!Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "当前存储卡不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        mProgressDialog = ProgressDialog.show(this, null, "正在加载...");
        new Thread(){
            @Override
            public void run() {
                Uri mImgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver cr = MainActivity.this.getContentResolver();
                Cursor cursor = cr.query(mImgUri, null, MediaStore.Images.Media.MIME_TYPE
                        + " = ? or " + MediaStore.Images.Media.MIME_TYPE
                        + " = ?", new String[]{"image/jpeg", "image/png"},
                        MediaStore.Images.Media.DATE_MODIFIED);
                Set<String> mDirPath = new HashSet<String>();
                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));

                    File parentFile = new File(path).getParentFile();
                    if(parentFile == null) {
                        continue;
                    }
                    String dirPath = parentFile.getAbsolutePath();

                    FolderBean folderBean = null;
                    if(mDirPath.contains(dirPath)) {
                        continue;
                    }
                    else {
                        mDirPath.add(dirPath);
                        folderBean = new FolderBean();
                        folderBean.setDir(dirPath);
                        folderBean.setFirstImgPath(path);
                    }
                    if(parentFile.list() == null) {
                        continue;
                    }
                    int picSize = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            if(filename.endsWith(".jpg")
                                    || filename.endsWith(".jpeg")
                                    || filename.endsWith(".png"))
                                return true;
                            return false;
                        }
                    }).length;
                    folderBean.setCount(picSize);

                    mFolderBeans.add(folderBean);

                    if(picSize > mMaxCount) {
                        mMaxCount = picSize;
                        mCurrentDir = parentFile;
                    }
                }
                cursor.close();
                //通知handler扫描完成
                mHandler.sendEmptyMessage(DATA_LOADED);
            }
        }.start();

    }

    private void initView() {
        mGridView = (GridView) findViewById(R.id.id_gridView);
        mBottomLy = (RelativeLayout) findViewById(R.id.id_bottom_ly);
        mDirName = (TextView) findViewById(R.id.id_dir_name);
        mDirCount = (TextView) findViewById(R.id.id_dir_count);
    }

}
