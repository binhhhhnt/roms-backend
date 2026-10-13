package com.roms.service;

import com.roms.dto.request.LoginRequest;
import com.roms.dto.response.AuthResult;
import com.roms.dto.response.UserProfileResponse;
import com.roms.security.UserPrincipal;

public interface AuthService {

    AuthResult login(LoginRequest request, String deviceId);

    AuthResult refresh(String rawRefreshToken, String deviceId);

    void logout(UserPrincipal principal);

    UserProfileResponse getProfile(Long userId);
}
