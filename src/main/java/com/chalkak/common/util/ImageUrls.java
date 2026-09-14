package com.chalkak.common.util;

public final class ImageUrls {

  public static final String BASE_PATH = "/api/v1/images";

  private ImageUrls() {
  }

  public static String download(Long imageId) {
    return BASE_PATH + "/" + imageId;
  }
}
