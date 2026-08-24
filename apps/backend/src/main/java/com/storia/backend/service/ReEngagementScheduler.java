package com.storia.backend.service;

import com.storia.backend.entity.User;
import com.storia.backend.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 3일 이상 활동이 없는 유저에게 재참여 푸시를 하루 한 번 발송한다.
 * 유저가 다시 활동하면 {@code ConversationService#getOrCreateConversation}에서
 * {@code reengagementPushSent}가 false로 리셋되어, 다음 휴면기에 다시 대상이 된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReEngagementScheduler {

    private static final Duration IDLE_THRESHOLD = Duration.ofDays(3);

    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;

    @Scheduled(cron = "0 0 20 * * *")
    @Transactional
    public void sendReEngagementPushes() {
        Instant threshold = Instant.now().minus(IDLE_THRESHOLD);
        List<User> idleUsers =
                userRepository.findByLastActiveAtBeforeAndReengagementPushSentFalse(threshold);

        for (User user : idleUsers) {
            pushNotificationService.sendReEngagement(user.getFcmToken());
            user.setReengagementPushSent(true);
        }

        if (!idleUsers.isEmpty()) {
            log.info("재참여 푸시 {}건 발송", idleUsers.size());
        }
    }
}
