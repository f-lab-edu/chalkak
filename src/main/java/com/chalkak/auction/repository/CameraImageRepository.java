package com.chalkak.auction.repository;

import com.chalkak.auction.entity.CameraImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CameraImageRepository extends JpaRepository<CameraImage, Long> {
    List<CameraImage> findByCameraId(Long cameraId);
    Optional<CameraImage> findFirstByCameraIdOrderByIdAsc(Long cameraId);

}
