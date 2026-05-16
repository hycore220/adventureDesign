package com.example.spring_boot_1.RecommendationData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecommendationWeightRepository extends JpaRepository<RecommendationWeight, Integer> {
    List<RecommendationWeight> findByUserUserName(String userName);

    List<RecommendationWeight> findByUserId(int userId);

    @Query("select recommendationWeight from RecommendationWeight recommendationWeight where recommendationWeight.bookmark.PARAStatus = :paraStatus")
    List<RecommendationWeight> findByBookmarkParaStatus(@Param("paraStatus") String paraStatus);

    List<RecommendationWeight> findByBookmarkId(int bookmarkId);

    Optional<RecommendationWeight> findByUserIdAndBookmarkId(int userId, int bookmarkId);

    List<RecommendationWeight> findTop3ByUserIdOrderByWeightValueDesc(int userId);
}
