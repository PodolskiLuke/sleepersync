package com.sleepersync.repository;

import com.sleepersync.model.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByPlayerId(String playerId);

    List<Player> findByPosition(String position);

    List<Player> findByTeam(String team);

    List<Player> findByActiveTrue();

    List<Player> findByFullNameContainingIgnoreCase(String name);

    List<Player> findByStatus(String status);
}
