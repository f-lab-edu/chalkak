package com.chalkak.auction.entity;

import com.chalkak.auction.exception.CameraErrorCode;
import com.chalkak.common.entity.BaseEntity;
import com.chalkak.common.exception.BusinessException;
import com.chalkak.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "cameras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Camera extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CameraCategory category;

    @Column(nullable = false)
    String brand;

    @Column(nullable = false)
    String modelName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CameraConditionGrade conditionGrade;

    @Column(nullable = false)
    String description;

    private Camera(User owner, CameraCategory category, String brand, String modelName,
            CameraConditionGrade conditionGrade, String description) {
        validateOwner(owner);
        validateCategory(category);
        validateBrand(brand);
        validateModelName(modelName);
        validateConditionGrade(conditionGrade);
        validateDescription(description);

        this.owner = owner;
        this.category = category;
        this.brand = brand;
        this.modelName = modelName;
        this.conditionGrade = conditionGrade;
        this.description = description;
    }

    public static Camera register(User owner, CameraCategory category, String brand, String modelName,
            CameraConditionGrade conditionGrade, String description) {
        return new Camera(owner, category, brand, modelName, conditionGrade, description);
    }

    private static void validateOwner(User owner) {
        if (owner == null) {
            throw new BusinessException(CameraErrorCode.INVALID_OWNER);
        }
    }

    private static void validateCategory(CameraCategory category) {
        if (category == null) {
            throw new BusinessException(CameraErrorCode.INVALID_CATEGORY);
        }
    }

    private static void validateBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            throw new BusinessException(CameraErrorCode.INVALID_BRAND);
        }
    }

    private static void validateModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new BusinessException(CameraErrorCode.INVALID_MODEL_NAME);
        }
    }

    private static void validateConditionGrade(CameraConditionGrade conditionGrade) {
        if (conditionGrade == null) {
            throw new BusinessException(CameraErrorCode.INVALID_CONDITION_GRADE);
        }
    }

    private static void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new BusinessException(CameraErrorCode.INVALID_DESCRIPTION);
        }
    }
}
