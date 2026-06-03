package com.example.spring_boot_1.PushData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserDataId(int userId);

    Optional<PushSubscription> findByUserDataIdAndEndpoint(int userId, String endpoint);

    @Transactional
    long deleteByUserDataIdAndEndpoint(int userId, String endpoint);

    @Transactional
    long deleteByEndpoint(String endpoint);
}
