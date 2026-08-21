package com.chalkak.auction.fixture;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

public class MultipartFileFixture {

    public static MockMultipartFile image(String filename) {
        return new MockMultipartFile("images", filename, MediaType.IMAGE_JPEG_VALUE, "image-content".getBytes());
    }

    public static List<MultipartFile> images(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(i -> (MultipartFile) image("image" + i + ".jpg"))
            .toList();
    }
}
