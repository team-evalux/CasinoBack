package org.example.repo;

import org.example.model.Avatar;
import org.example.model.AvatarRarity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {

    List<Avatar> findByActifTrueOrderByPrixAsc();

    List<Avatar> findByActifTrueAndRareteOrderByPrixAsc(AvatarRarity rarete);

    Optional<Avatar> findFirstByActifTrueAndDefautTrue();
}
