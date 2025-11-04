package org.example.repo;

import org.example.model.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {
    List<UserItem> findByUserId(Long userId);
}
