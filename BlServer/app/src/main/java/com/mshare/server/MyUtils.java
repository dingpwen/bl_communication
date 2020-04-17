package com.mshare.server;

import android.graphics.Bitmap;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MyUtils {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;

    public static void setImage(ImageView ivShow, String content) {
        try {
            Map<EncodeHintType, Object> hints = null;
            String encoding = getEncoding(content);//获取编码格式
            if (encoding != null) {
                hints = new HashMap<EncodeHintType, Object>();
                hints.put(EncodeHintType.CHARACTER_SET, encoding);
            }
            BitMatrix result=new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE,350,350,hints);//通过字符串创建二维矩阵
            int width = result.getWidth();
            int height = result.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;//根据二维矩阵数据创建数组
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);//创建位图
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);//设置位图像素集为数组
            ivShow.setImageBitmap(bitmap);//显示二维码
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private static String getEncoding(CharSequence contents) {
        // Very crude at the moment
        for (int i = 0; i < contents.length(); i++) {
            if (contents.charAt(i) > 0xFF) {
                return "UTF-8";
            }
        }
        return null;
    }
}
