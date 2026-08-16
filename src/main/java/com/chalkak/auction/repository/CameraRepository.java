package com.chalkak.auction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chalkak.auction.entity.Camera;

@Repository
public interface CameraRepository extends JpaRepository<Camera, Long> {
}
