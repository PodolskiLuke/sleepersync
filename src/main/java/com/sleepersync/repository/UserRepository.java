package com.sleepersync.repository;

import com.sleepersync.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySleeperUsername(String sleeperUsername);

    Optional<User> findBySleeperUserId(String sleeperUserId);

    boolean existsByEmail(String email);

    boolean existsBySleeperUsername(String sleeperUsername);
}
