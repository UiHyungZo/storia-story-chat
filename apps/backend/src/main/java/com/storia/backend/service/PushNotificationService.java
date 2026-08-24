package com.storia.backend.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends a "new message" FCM push to a user's registered device (PRD 3.6 — 원격 푸시).
 * No-ops if Firebase isn't configured (see FirebaseConfig) or the user has no
 * registered token yet, so callers don't need to check either condition.
 */
@Service
@Slf4j
public class PushNotificationService {

    public void sendNewMessage(String fcmToken, String characterName, String content) {
        if (fcmToken == null || fcmToken.isBlank() || FirebaseApp.getApps().isEmpty()) {
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(characterName)
                        .setBody(content)
                        .build())
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 푸시 발송 실패 (token={})", fcmToken, e);
        }
    }
}
