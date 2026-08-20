package com.chalkak.auction.entity;

import com.chalkak.auction.exception.CameraImageErrorCode;
import com.chalkak.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "camera_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners(AuditingEntityListener.class)
public class CameraImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    Camera camera;

    @Column(nullable = false)
    String imageKey;

    @CreatedDate
    @Column(updatable = false)
    LocalDateTime createdAt;

    private CameraImage(Camera camera, String imageKey) {
        validateImageKey(imageKey);

        this.camera = camera;
        this.imageKey = imageKey;
    }

    public static CameraImage attach(Camera camera, String imageKey) {
        return new CameraImage(camera, imageKey);
    }

    private static void validateImageKey(String imageKey) {
        if (imageKey.isBlank()) {
            throw new BusinessException(CameraImageErrorCode.INVALID_IMAGE_KEY);
        }
    }
}
