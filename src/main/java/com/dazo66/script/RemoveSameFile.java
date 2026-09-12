package com.dazo66.script;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class RemoveSameFile {

    public static void main(String[] args) {
        if (args.length == 0) {
            return;
        }
        String path = args[0];

        File[] list = new File(path).listFiles();
        Map<String, List<File>> map = new HashMap<>();
        for (File file : list) {
            if (file.isFile()) {
                ArrayList<File> value = new ArrayList<>();
                List<File> files = map.putIfAbsent(getMD5One(file.getAbsolutePath()), value);
                if (files != null) {
                    files.add(file);
                } else {
                    value.add(file);
                }
            }
        }
        List<File> deleteList = new ArrayList<>();
        map.forEach((s, files) -> files.sort(Comparator.comparingLong(File::lastModified).reversed()));
        map.forEach((s, files) -> {
            if (files.size() > 1) {
                for (int i = 1; i < files.size(); i++) {
                    File file = files.get(i);
                    System.out.println("delete " + file.getName());
                    deleteList.add(file);

                }
            }
        });
        for (File file : deleteList) {
            file.delete();
        }
        System.out.println(deleteList);

    }


    private final static String[] strHex = {"0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

    public static String getMD5One(String path) {
        StringBuffer sb = new StringBuffer();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(FileUtils.readFileToByteArray(new File(path)));
            for (int i = 0; i < b.length; i++) {
                int d = b[i];
                if (d < 0) {
                    d += 256;
                }
                int d1 = d / 16;
                int d2 = d % 16;
                sb.append(strHex[d1] + strHex[d2]);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

}
