package com.medirag.entity.dto;

import lombok.Data;

/**
 * 用户信息更新 DTO
 */
@Data
public class UserUpdateDTO {

    private String nickname;

    private String avatar;

    private String phone;

    private String healthProfile;
}
