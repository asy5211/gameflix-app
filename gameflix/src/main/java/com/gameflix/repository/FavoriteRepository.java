package com.gameflix.repository;

import com.gameflix.model.Favorite;
import com.gameflix.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUser_IdAndGame_Id(Long userId, Long gameId);

    void deleteByUser_IdAndGame_Id(Long userId, Long gameId);

    @Query("select f.game from Favorite f where f.user.id = :userId order by f.game.title")
    List<Game> findGamesByUserId(@Param("userId") Long userId);
}
