package com.chalkak.user.entity;

import com.chalkak.common.entity.BaseEntity;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.exception.UserErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity implements Serializable {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^01\\d-\\d{3,4}-\\d{4}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String password;

    @Column(nullable = false, unique = true)
    String phone;

    @Column(nullable = false, unique = true)
    String nickname;

    private User(String email, String encodedPassword, String phone, String nickname) {
        validateEmail(email);
        validatePhone(phone);
        validateNickname(nickname);

        this.email = email;
        this.password = encodedPassword;
        this.phone = phone;
        this.nickname = nickname;
    }

    public static User signUp(String email, String encodedPassword, String phone, String nickname) {
        return new User(email, encodedPassword, phone, nickname);
    }

    private static void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_EMAIL_FORMAT);
        }
    }

    private static void validatePhone(String phone) {
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(UserErrorCode.INVALID_PHONE_FORMAT);
        }
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || nickname.length() < NICKNAME_MIN_LENGTH || nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(UserErrorCode.INVALID_NICKNAME_LENGTH);
        }
    }
}
