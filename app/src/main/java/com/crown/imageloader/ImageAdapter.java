package com.crown.imageloader;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.crown.imageloader.util.ImgLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageAdapter extends BaseAdapter {

        private static Set<String> mSelectedImg = new HashSet<String>();

        private String mDirpath;
        private List<String> mImgPaths;
        private LayoutInflater mInflater;

        public ImageAdapter(Context context, List<String> mDatas, String dirPath) {
            this.mDirpath = dirPath;
            this.mImgPaths = mDatas;
            mInflater = LayoutInflater.from(context);
        }
        @Override
        public int getCount() {
            return mImgPaths.size();
        }

        @Override
        public Object getItem(int position) {
            return mImgPaths.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewHolder viewHolder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.gridview_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.mImg = (ImageView) convertView.findViewById(R.id.id_item_image);
                viewHolder.mSelect = (ImageButton) convertView.findViewById(R.id.id_item_select);
                convertView.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            viewHolder.mImg.setImageResource(R.drawable.pictures_no);
            viewHolder.mSelect.setImageResource(R.drawable.picture_unselected);
            viewHolder.mImg.setColorFilter(null);

            ImgLoader.getInstance(3, ImgLoader.Type.LIFO)
                    .loadImage(mDirpath + "/" + mImgPaths.get(position), viewHolder.mImg);
            //使用图片的完整路径，防止不同文件加下文明名相同的情况
            final String filePath = mDirpath + "/" + mImgPaths.get(position);
            viewHolder.mImg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mSelectedImg.contains(filePath)) {
                        mSelectedImg.remove(filePath);
                        viewHolder.mImg.setColorFilter(null);
                        viewHolder.mSelect.setImageResource(R.drawable.picture_unselected);
                    }
                    else {
                        mSelectedImg.add(filePath);
                        viewHolder.mImg.setColorFilter(Color.parseColor("#77000000"));
                        viewHolder.mSelect.setImageResource(R.drawable.pictures_selected);
                    }
                    //notifyDataSetChanged(); 使用notifyDataChanged会闪屏
                }
            });

            if (mSelectedImg.contains(filePath)) {
                viewHolder.mImg.setColorFilter(Color.parseColor("#77000000"));
                viewHolder.mSelect.setImageResource(R.drawable.pictures_selected);
            }
            else {

            }
            return convertView;
        }

        private class ViewHolder {
            ImageView mImg;
            ImageButton mSelect;
        }
    }