package com.storia.backend.controller;

import com.storia.backend.dto.DeviceTokenRequest;
import com.storia.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registers a client's FCM device token so the backend can push new-message
 * notifications when the app is backgrounded/killed (PRD 3.6). The client-side
 * @react-native-firebase wiring that actually obtains a token needs a real
 * Firebase project's config files — see HANDOFF.md.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final UserService userService;

    @PutMapping("/token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerToken(
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody DeviceTokenRequest request) {
        userService.updateFcmToken(deviceId, request.token());
    }
}
