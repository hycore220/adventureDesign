package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.ParaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecommendationWeightRepository extends JpaRepository<RecommendationWeight, Integer> {
    List<RecommendationWeight> findByBookmarkUserDataUserName(String userName);

    List<RecommendationWeight> findByBookmarkUserDataId(int userId);

    @Query("select recommendationWeight from RecommendationWeight recommendationWeight where recommendationWeight.bookmark.PARAStatus = :paraStatus")
    List<RecommendationWeight> findByBookmarkParaStatus(@Param("paraStatus") ParaStatus paraStatus);

    List<RecommendationWeight> findByBookmarkId(int bookmarkId);

    Optional<RecommendationWeight> findFirstByBookmarkId(int bookmarkId);
}
