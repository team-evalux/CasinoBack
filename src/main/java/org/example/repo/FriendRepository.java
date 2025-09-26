package org.example.repo;

import org.example.model.Friend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {
    List<Friend> findByOwnerEmail(String ownerEmail);
    Optional<Friend> findByOwnerEmailAndFriendEmail(String ownerEmail, String friendEmail);
}
