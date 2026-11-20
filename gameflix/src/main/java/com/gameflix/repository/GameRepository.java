package com.gameflix.repository;

import com.gameflix.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllByOrderByTitleAsc();

    // Case-insensitive "contains" search on the title.
    List<Game> findByTitleContainingIgnoreCaseOrderByTitleAsc(String title);
}
