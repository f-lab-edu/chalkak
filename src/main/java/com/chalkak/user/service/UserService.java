package com.chalkak.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.controller.request.UserRequest;
import com.chalkak.user.entity.User;
import com.chalkak.user.exception.UserErrorCode;
import com.chalkak.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(UserRequest request) {
        validateDuplicate(request.email(), request.phone());

        User user = User.builder()
            .email(request.email())
            .encodedPassword(passwordEncoder.encode(request.password()))
            .phone(request.phone())
            .build();

        userRepository.save(user);
    }

    private void validateDuplicate(String email, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_PHONE);
        }
    }
}
