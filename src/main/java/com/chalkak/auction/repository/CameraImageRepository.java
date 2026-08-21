package com.chalkak.auction.repository;

import com.chalkak.auction.entity.CameraImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CameraImageRepository extends JpaRepository<CameraImage, Long> {
}
