package com.storia.backend.service;

import com.storia.backend.entity.User;
import com.storia.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public void updateFcmToken(String deviceId, String fcmToken) {
        User user = userRepository.findByDeviceId(deviceId)
                .orElseGet(() -> userRepository.save(new User(deviceId)));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }
}
