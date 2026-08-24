package com.storia.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Initializes the Firebase Admin SDK for FCM push if FIREBASE_CREDENTIALS_PATH
 * (a service-account JSON key file) is set. Without it, push is silently
 * disabled — same graceful-degradation pattern as GeminiProperties/GEMINI_API_KEY.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseConfig {

    private final FcmProperties properties;

    @PostConstruct
    public void init() {
        if (!properties.isConfigured()) {
            log.warn("FIREBASE_CREDENTIALS_PATH가 설정되지 않아 FCM 푸시가 비활성화됩니다.");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try (FileInputStream serviceAccount = new FileInputStream(properties.credentialsPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (Exception e) {
            log.warn("Firebase 초기화 실패 — FCM 푸시가 비활성화됩니다.", e);
        }
    }
}
