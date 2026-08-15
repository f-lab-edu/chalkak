package com.chalkak.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chalkak.user.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
