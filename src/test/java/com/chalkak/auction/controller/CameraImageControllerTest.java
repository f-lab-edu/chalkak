package com.chalkak.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.auction.entity.Camera;
import com.chalkak.auction.entity.CameraImage;
import com.chalkak.auction.fixture.CameraFixture;
import com.chalkak.auction.fixture.MultipartFileFixture;
import com.chalkak.auction.repository.CameraImageRepository;
import com.chalkak.auction.repository.CameraRepository;
import com.chalkak.file.service.FileStorage;
import com.chalkak.user.entity.User;
import com.chalkak.user.fixture.UserFixture;
import com.chalkak.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CameraImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CameraRepository cameraRepository;

    @Autowired
    private CameraImageRepository cameraImageRepository;

    @Autowired
    private FileStorage fileStorage;

    @Test
    void 로그인하지_않아도_200과_이미지_바이트를_응답한다() throws Exception {
        CameraImage cameraImage = uploadAndAttach("photo.png");

        MvcResult result = mockMvc.perform(get("/api/v1/images/{imageId}", cameraImage.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
            .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("image-content".getBytes());
    }

    @Test
    void 존재하지_않는_이미지를_조회하면_404를_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/images/{imageId}", -1L))
            .andExpect(status().isNotFound());
    }

    private CameraImage uploadAndAttach(String filename) {
        User owner = userRepository.save(UserFixture.create());
        Camera camera = cameraRepository.save(CameraFixture.create(owner));
        String key = fileStorage.upload(MultipartFileFixture.image(filename));
        return cameraImageRepository.save(CameraImage.attach(camera, key));
    }
}
