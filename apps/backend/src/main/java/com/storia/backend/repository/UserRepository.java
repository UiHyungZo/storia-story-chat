package com.storia.backend.repository;

import com.storia.backend.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByDeviceId(String deviceId);

    List<User> findByLastActiveAtBeforeAndReengagementPushSentFalse(Instant threshold);
}
