package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/recommendation-weights")
public class RecommendationWeightController {

    private final RecommendationWeightService recommendationWeightService;

    public RecommendationWeightController(RecommendationWeightService recommendationWeightService) {
        this.recommendationWeightService = recommendationWeightService;
    }

    @PostMapping
    public ResponseEntity<RecommendationWeight> create(@RequestBody RecommendationWeight recommendationWeight) {
        return ResponseEntity.ok(recommendationWeightService.create(recommendationWeight));
    }

    @GetMapping
    public ResponseEntity<List<RecommendationWeight>> getAll(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String paraStatus,
            @RequestParam(required = false) Integer bookmarkId
    ) {
        if (userId != null) {
            return ResponseEntity.ok(recommendationWeightService.getByUserId(userId));
        }
        if (userName != null) {
            return ResponseEntity.ok(recommendationWeightService.getByUserName(userName));
        }
        if (paraStatus != null) {
            return ResponseEntity.ok(recommendationWeightService.getByParaStatus(paraStatus));
        }
        if (bookmarkId != null) {
            return ResponseEntity.ok(recommendationWeightService.getByBookmarkId(bookmarkId));
        }
        return ResponseEntity.ok(recommendationWeightService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecommendationWeight> getById(@PathVariable int id) {
        return ResponseEntity.ok(recommendationWeightService.getById(id));
    }

    @GetMapping("/users/{userId}/top-bookmarks")
    public ResponseEntity<List<LinkData>> getTop3BookmarksByUserId(@PathVariable int userId) {
        return ResponseEntity.ok(recommendationWeightService.getTop3BookmarksByUserId(userId));
    }

    @PostMapping("/{id}/calculated-weight")
    public ResponseEntity<RecommendationWeight> updateCalculatedWeight(@PathVariable int id) {
        return ResponseEntity.ok(recommendationWeightService.updateCalculatedWeight(id));
    }

    @PostMapping("/users/{userId}/calculated-weights")
    public ResponseEntity<List<RecommendationWeight>> updateCalculatedWeightsByUserId(@PathVariable int userId) {
        return ResponseEntity.ok(recommendationWeightService.updateCalculatedWeightsByUserId(userId));
    }

    @PostMapping("/users/{userId}/interest-similarities")
    public ResponseEntity<List<RecommendationWeight>> updateSimilaritiesWithUserInterest(@PathVariable int userId) {
        return ResponseEntity.ok(recommendationWeightService.updateSimilaritiesWithUserInterest(userId));
    }

    @PostMapping("/users/{userId}/refresh")
    public ResponseEntity<List<RecommendationWeight>> refreshUserRecommendations(@PathVariable int userId) {
        return ResponseEntity.ok(recommendationWeightService.refreshUserRecommendations(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecommendationWeight> update(
            @PathVariable int id,
            @RequestBody RecommendationWeight recommendationWeight
    ) {
        return ResponseEntity.ok(recommendationWeightService.update(id, recommendationWeight));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        recommendationWeightService.delete(id);
        return ResponseEntity.ok("삭제 완료");
    }

    @PostMapping("/{id}/embedding")
    public ResponseEntity<RecommendationWeight> updateEmbedding(@PathVariable int id) {
        return ResponseEntity.ok(recommendationWeightService.updateEmbedding(id));
    }

    @PostMapping("/{id}/similarity")
    public ResponseEntity<RecommendationWeight> updateSimilarityWithText(
            @PathVariable int id,
            @RequestBody SimilarityTextRequest request
    ) {
        return ResponseEntity.ok(recommendationWeightService.updateSimilarityWithText(id, request.text()));
    }

    @PostMapping("/{sourceId}/similarity/{targetId}")
    public ResponseEntity<RecommendationWeight> updateSimilarityWithRecommendationWeight(
            @PathVariable int sourceId,
            @PathVariable int targetId
    ) {
        return ResponseEntity.ok(recommendationWeightService.updateSimilarityWithRecommendationWeight(sourceId, targetId));
    }

    public record SimilarityTextRequest(String text) {
    }
}
