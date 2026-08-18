package com.chalkak.auction.fixture;

import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraImage;

public class CameraImageFixture {

    public static final String DEFAULT_IMAGE_KEY = "camera/default-image.jpg";

    public static CameraImage create() {
        return create(CameraFixture.create());
    }

    public static CameraImage create(Camera camera) {
        return create(camera, DEFAULT_IMAGE_KEY);
    }

    public static CameraImage create(Camera camera, String imageKey) {
        return CameraImage.attach(camera, imageKey);
    }
}
