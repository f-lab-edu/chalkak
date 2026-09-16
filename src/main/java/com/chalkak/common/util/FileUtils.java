package com.chalkak.common.util;

public final class FileUtils {

    private FileUtils() {
    }

    public static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? "" : filename.substring(dotIndex).toLowerCase();
    }
}
