package com.img.crop.utils;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.Formatter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.StringTokenizer;

/*
 * Copyright (C) 2017
 * 版权所有
 *
 * 功能描述：
 * 作者：huangyong
 * 创建时间：2018/1/5
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class FileUtil {
    private static final String TAG = "FileUtil";

    public static String SDCARD_PATH = Environment.getExternalStorageDirectory()
            .getAbsolutePath();
    private static DecimalFormat mFormater = new DecimalFormat("#.##");

    /**
     * 判断sd卡是否存在
     */
    public static boolean isSDCardExist() {
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            return true;
        } else
            return false;
    }

    /**
     * 判断文件夹是否存在 ， 若不存在 则创建
     */
    public static void isExists(String path) {

        StringTokenizer st = new StringTokenizer(path, "/");
        String path1 = st.nextToken() + "/";
        String path2 = path1;
        while (st.hasMoreTokens()) {
            path1 = st.nextToken() + "/";
            path2 += path1;
            File inbox = new File(path2);
            if (!inbox.exists())
                inbox.mkdir();
        }
    }

    public static void cacheStringToFile(String str, String filename) {
        File f = new File(filename);
        if (f.exists()) {
            f.delete();
        }
        try {
            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f, true);
            fos.write(str.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeStream(fos);
        }
    }

    public static long getFileSize(String path) {
        File file = new File(path);
        long s = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                s = fis.available();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeStream(fis);
            }
        } else {
            //Log.e(TAG, "file is not exist: ");
        }
        return s / 1024;

    }

    public static byte[] fileToByte(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            byte[] buffer = new byte[fin.available()];
            fin.read(buffer);
            fin.close();
            return buffer;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeStream(fin);
        }

        return null;
    }

    public static long getCacheSize(String path) {
        long total = 0;
        File file = new File(path);
        if (file == null || !file.exists()) {
            return total;
        }
        if (file.isFile()) {
            total = file.length();
        } else if (file.isDirectory()) {
            File[] fileList = file.listFiles();
            if (fileList != null) {
                for (int i = 0; i < fileList.length; i++) {
                    total += getCacheSize(fileList[i].getAbsolutePath());
                }
            }
        }

        return total;
    }

    public static String calculateSizeToString(Context ctx, long size) {
        return Formatter.formatFileSize(ctx, size);
    }

    public static boolean deleteFiles(String path) {
        File file = new File(path);
        boolean flag = false;
        if (file == null || !file.exists()) {
            return flag;
        }
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File[] fileList = file.listFiles();
            for (int i = 0; i < fileList.length; i++) {
                flag = deleteFiles(fileList[i].getAbsolutePath());
                // if(!flag){
                // break;
                // }
            }
        }
        return flag;
    }

    public static boolean checkFilesIsNeedDelete(String path, long intervalTime) {
        File file = new File(path);
        if (file == null || !file.exists()) {
            return false;
        }
        if (file.isFile()) {
            return deleteFileByIntervalTime(file, intervalTime);
        } else {
            File[] fileList = file.listFiles();
            if (fileList != null && fileList.length > 0) {
                for (int i = 0; i < fileList.length; i++) {
                    File temp = fileList[i];
                    if (temp.isDirectory()) {
                        checkFilesIsNeedDelete(temp.getAbsolutePath(),
                                intervalTime);
                    } else {
                        deleteFileByIntervalTime(temp, intervalTime);
                    }
                }
            }
        }
        return true;
    }

    public static boolean deleteFileByIntervalTime(File file, long intervalTime) {
        long time = System.currentTimeMillis();
        if (time - file.lastModified() > intervalTime) {
            file.delete();
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断某个文件最后修改时间是否大于传入时间
     *
     * @param intervalTime 毫秒
     */
    public static boolean isFileTimeOver(String filePath, long intervalTime) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        }
        if (System.currentTimeMillis() - file.lastModified() > intervalTime) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 将数据写入一个文件
     *
     * @param destFilePath 要创建的文件的路径
     * @param data         待写入的文件数据
     * @param startPos     起始偏移量
     * @param length       要写入的数据长度
     * @return 成功写入文件返回true, 失败返回false
     */
    public static boolean writeFile(String destFilePath, byte[] data,
                                    int startPos, int length) {
        try {
            if (!createFile(destFilePath)) {
                return false;
            }
            FileOutputStream fos = new FileOutputStream(destFilePath);
            fos.write(data, startPos, length);
            fos.flush();
            if (null != fos) {
                fos.close();
                fos = null;
            }
            return true;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 从一个输入流里写文件
     *
     * @param destFilePath 要创建的文件的路径
     * @param in           要读取的输入流
     * @return 写入成功返回true, 写入失败返回false
     */
    public static boolean writeFile(String destFilePath, InputStream in) {
        try {
            if (!createFile(destFilePath)) {
                return false;
            }
            FileOutputStream fos = new FileOutputStream(destFilePath);
            int readCount = 0;
            int len = 1024;
            byte[] buffer = new byte[len];
            while ((readCount = in.read(buffer)) != -1) {
                fos.write(buffer, 0, readCount);
            }
            fos.flush();
            if (null != fos) {
                fos.close();
                fos = null;
            }
            if (null != in) {
                in.close();
                in = null;
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean appendFile(String filename, byte[] data, int datapos, int dataLength) {
        RandomAccessFile rf = null;
        try {
            createFile(filename);
            rf = new RandomAccessFile(filename, "rw");
            rf.seek(rf.length());
            rf.write(data, datapos, dataLength);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeStream(rf);
        }

        return true;
    }

    /**
     * 读取文件，返回以byte数组形式的数据
     *
     * @param filePath 要读取的文件路径名
     */
    public static byte[] readFile(String filePath) {
        try {
            if (isFileExist(filePath)) {
                FileInputStream fi = new FileInputStream(filePath);
                return readInputStream(fi);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 从一个数量流里读取数据,返回以byte数组形式的数据。 </br></br> 需要注意的是，如果这个方法用在从本地文件读取数据时，一般不会遇到问题，
     * 但如果是用于网络操作，就经常会遇到一些麻烦(available()方法的问题)。所以如果是网络流不应该使用这个方法。
     *
     * @param in 要读取的输入流
     */
    public static byte[] readInputStream(InputStream in) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            byte[] b = new byte[in.available()];
            int length = 0;
            while ((length = in.read(b)) != -1) {
                os.write(b, 0, length);
            }

            b = os.toByteArray();

            in.close();
            in = null;

            os.close();
            os = null;

            return b;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 读取网络流
     */
    public static byte[] readNetWorkInputStream(InputStream in) {
        ByteArrayOutputStream os = null;
        try {
            os = new ByteArrayOutputStream();

            int readCount = 0;
            int len = 1024;
            byte[] buffer = new byte[len];
            while ((readCount = in.read(buffer)) != -1) {
                os.write(buffer, 0, readCount);
            }

            in.close();
            in = null;

            return os.toByteArray();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeStream(os);
        }
        return null;
    }

    /**
     * 将一个文件拷贝到另外一个地方
     *
     * @param sourceFile    源文件地址
     * @param destFile      目的地址
     * @param shouldOverlay 是否覆盖
     */
    public static boolean copyFiles(String sourceFile, String destFile, boolean shouldOverlay) {
        try {
            if (shouldOverlay) {
                deleteFile(destFile);
            }
            FileInputStream fi = new FileInputStream(sourceFile);
            writeFile(destFile, fi);
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 判断文件是否存在
     *
     * @param filePath 路径名
     */
    public static boolean isFileExist(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    /**
     * 创建一个文件，创建成功返回true
     */
    public static boolean createFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }

                return file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 删除一个文件
     *
     * @param filePath 要删除的文件路径名
     * @return true if this file was deleted, false otherwise
     */
    public static boolean deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 删除 directoryPath目录下的所有文件，包括删除删除文件夹
     */
    public static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] listFiles = dir.listFiles();
            if (listFiles != null) {
                for (int i = 0; i < listFiles.length; i++) {
                    deleteDirectory(listFiles[i]);
                }
            }
        }
        dir.delete();
    }

    /**
     * clear all file and dir which in root directory
     * @param directory
     */
    public static void clearDirFiles(File directory) {

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                clearDirFiles(file);
            }

            file.delete();
        }
    }

    /**
     * 字符串转流
     */
    public static InputStream String2InputStream(String str) {
        ByteArrayInputStream stream = new ByteArrayInputStream(str.getBytes());
        return stream;
    }

    /**
     * 流转字符串
     */
    public static String inputStream2String(InputStream is) {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        StringBuffer buffer = new StringBuffer();
        String line = "";

        try {
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer.toString();
    }

    /**
     * 批量更改文件后缀
     */
    public static void reNameSuffix(File dir, String oldSuffix, String newSuffix) {
        if (dir.isDirectory()) {
            File[] listFiles = dir.listFiles();
            for (int i = 0; i < listFiles.length; i++) {
                reNameSuffix(listFiles[i], oldSuffix, newSuffix);
            }
        } else {
            dir.renameTo(new File(dir.getPath().replace(oldSuffix, newSuffix)));
        }
    }

    public static void cleanDirectory(File directory) throws IOException,
            IllegalArgumentException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) { // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    public static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent) {
                    throw new FileNotFoundException("File does not exist: "
                            + file);
                }
                String message = "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }

    public static FileInputStream openInputStream(String filePath)
            throws IOException {
        return openInputStream(new File(filePath));
    }

    public static FileInputStream openInputStream(File file) throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                throw new IOException("File '" + file
                        + "' exists but is a directory");
            }
            if (file.canRead() == false) {
                throw new IOException("File '" + file + "' cannot be read");
            }
        } else {
            throw new FileNotFoundException("File '" + file
                    + "' does not exist");
        }
        return new FileInputStream(file);
    }

    public static FileOutputStream openOutputStream(String filePath)
            throws IOException {
        return openOutputStream(new File(filePath));
    }

    public static FileOutputStream openOutputStream(File file)
            throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                throw new IOException("File '" + file
                        + "' exists but is a directory");
            }
            if (file.canWrite() == false) {
                throw new IOException("File '" + file
                        + "' cannot be written to");
            }
        } else {
            File parent = file.getParentFile();
            if (parent != null && parent.exists() == false) {
                if (parent.mkdirs() == false) {
                    throw new IOException("File '" + file
                            + "' could not be created");
                }
            }
        }
        return new FileOutputStream(file);
    }

    /**
     * 保存文件
     */
    public static boolean save(File file, byte[] data) {
        OutputStream os = null;
        try {
            os = openOutputStream(file);
            os.write(data, 0, data.length);

            return true;
        } catch (Exception e) {
            //LogUtil.d("FileUtil", "save " + file + "error! " + e.getMessage());
            return false;
        } finally {
            closeStream(os);
        }
    }

    private static void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {

            }
        }
    }

    public static String mkdirs(String path) {
        File f = new File(path);
        if (!f.exists()) {
            boolean succ = f.mkdirs();
        }

        return path;
    }

    /**
     * @param length 文件长度
     * @return 带有合适单位名称的文件大小
     */
    public static String getSizeFormatText(long length) {
        if (length <= 0)
            return "0KB";

        String str = "B";
        double result = (double) length;
        if (length < 1024) {
            return "1KB";
        }
        // 以1024为界，找到合适的文件大小单位
        if (result >= 1024) {
            str = "KB";
            result /= 1024;
            if (result >= 1024) {
                str = "MB";
                result /= 1024;
            }
            if (result >= 1024) {
                str = "GB";
                result /= 1024;
            }
        }
        String sizeString = null;

        // 按照需求设定文件的精度
        // MB 和 GB 保留两位小数
        if (str.equals("MB") || str.equals("GB")) {
            sizeString = mFormater.format(result);
        }
        // B 和 KB 保留到各位
        else
            sizeString = Integer.toString((int) result);
        return sizeString + str;
    }
}
