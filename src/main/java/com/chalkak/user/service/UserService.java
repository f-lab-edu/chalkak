package com.chalkak.user.service;

import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.controller.request.UserRequest;
import com.chalkak.user.entity.User;
import com.chalkak.user.exception.UserErrorCode;
import com.chalkak.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(UserRequest request) {
        validateDuplicate(request.email(), request.phone());

        String encodePwd = passwordEncoder.encode(request.password());
        User user = User.signUp(request.email(), encodePwd, request.phone());

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
