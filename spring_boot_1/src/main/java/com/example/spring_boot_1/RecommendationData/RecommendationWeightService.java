package com.example.spring_boot_1.RecommendationData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.spring_boot_1.LinkData.LinkData;

@Service
public class RecommendationWeightService {

    private static final double RECENCY_HALF_LIFE_DAYS = 30.0;

    private final RecommendationWeightRepository recommendationWeightRepository;
    private final OpenAiEmbeddingService openAiEmbeddingService;

    public RecommendationWeightService(
            RecommendationWeightRepository recommendationWeightRepository,
            OpenAiEmbeddingService openAiEmbeddingService
    ) {
        this.recommendationWeightRepository = recommendationWeightRepository;
        this.openAiEmbeddingService = openAiEmbeddingService;
    }

    public RecommendationWeight create(RecommendationWeight recommendationWeight) {
        validateOwnerAndBookmark(recommendationWeight);
        return recommendationWeightRepository
                .findByUserIdAndBookmarkId(
                        recommendationWeight.getUser().getId(),
                        recommendationWeight.getBookmark().getId()
                )
                .map(existing -> updateFields(existing, recommendationWeight))
                .map(recommendationWeightRepository::save)
                .orElseGet(() -> recommendationWeightRepository.save(recommendationWeight));
    }

    public List<RecommendationWeight> getAll() {
        return recommendationWeightRepository.findAll();
    }

    public RecommendationWeight getById(int id) {
        return recommendationWeightRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("없는 추천 가중치 id입니다."));
    }

    public List<RecommendationWeight> getByUserName(String userName) {
        return recommendationWeightRepository.findByUserUserName(userName);
    }

    public List<RecommendationWeight> getByUserId(int userId) {
        return recommendationWeightRepository.findByUserId(userId);
    }

    public List<RecommendationWeight> getByParaStatus(String paraStatus) {
        return recommendationWeightRepository.findByBookmarkParaStatus(paraStatus);
    }

    public List<RecommendationWeight> getByBookmarkId(int bookmarkId) {
        return recommendationWeightRepository.findByBookmarkId(bookmarkId);
    }

    public List<LinkData> getTop3BookmarksByUserId(int userId) {
        return recommendationWeightRepository.findTop3ByUserIdOrderByWeightValueDesc(userId).stream()
                .map(RecommendationWeight::getBookmark)
                .toList();
    }

    public RecommendationWeight updateCalculatedWeight(int id) {
        RecommendationWeight recommendationWeight = getById(id);
        recommendationWeight.setWeightValue(calculateWeightValue(recommendationWeight));
        return recommendationWeightRepository.save(recommendationWeight);
    }

    public List<RecommendationWeight> updateCalculatedWeightsByUserId(int userId) {
        return recommendationWeightRepository.findByUserId(userId).stream()
                .map(recommendationWeight -> {
                    recommendationWeight.setWeightValue(calculateWeightValue(recommendationWeight));
                    return recommendationWeightRepository.save(recommendationWeight);
                })
                .toList();
    }

    public List<RecommendationWeight> updateSimilaritiesWithUserInterest(int userId) {
        List<RecommendationWeight> recommendationWeights = recommendationWeightRepository.findByUserId(userId).stream()
                .map(this::ensureEmbedding)
                .toList();

        List<Double> userInterestVector = buildUserInterestVector(recommendationWeights);
        recommendationWeights.forEach(recommendationWeight -> {
            if (recommendationWeight.isSnooze()) {
                recommendationWeight.setSimilarity(0);
                return;
            }

            List<Double> embedding = openAiEmbeddingService.fromJson(recommendationWeight.getEmbeddingVector());
            recommendationWeight.setSimilarity(openAiEmbeddingService.cosineSimilarity(embedding, userInterestVector));
        });
        return recommendationWeightRepository.saveAll(recommendationWeights);
    }

    public List<RecommendationWeight> refreshUserRecommendations(int userId) {
        List<RecommendationWeight> recommendationWeights = updateSimilaritiesWithUserInterest(userId);
        recommendationWeights.forEach(recommendationWeight ->
                recommendationWeight.setWeightValue(calculateWeightValue(recommendationWeight))
        );
        return recommendationWeightRepository.saveAll(recommendationWeights);
    }

    public RecommendationWeight update(int id, RecommendationWeight request) {
        validateOwnerAndBookmark(request);
        RecommendationWeight recommendationWeight = getById(id);
        updateFields(recommendationWeight, request);
        return recommendationWeightRepository.save(recommendationWeight);
    }

    public void delete(int id) {
        recommendationWeightRepository.deleteById(id);
    }

    public RecommendationWeight updateEmbedding(int id) {
        RecommendationWeight recommendationWeight = getById(id);
        String text = getEmbeddingText(recommendationWeight);
        List<Double> embedding = openAiEmbeddingService.createEmbedding(text);
        recommendationWeight.setEmbeddingText(text);
        recommendationWeight.setEmbeddingVector(openAiEmbeddingService.toJson(embedding));
        recommendationWeight.setEmbeddingModel(openAiEmbeddingService.getEmbeddingModel());
        recommendationWeight.setEmbeddingUpdatedAt(LocalDateTime.now());
        return recommendationWeightRepository.save(recommendationWeight);
    }

    public RecommendationWeight updateSimilarityWithText(int id, String text) {
        RecommendationWeight recommendationWeight = ensureEmbedding(getById(id));
        List<Double> savedEmbedding = openAiEmbeddingService.fromJson(recommendationWeight.getEmbeddingVector());
        List<Double> targetEmbedding = openAiEmbeddingService.createEmbedding(text);
        recommendationWeight.setSimilarity(openAiEmbeddingService.cosineSimilarity(savedEmbedding, targetEmbedding));
        return recommendationWeightRepository.save(recommendationWeight);
    }

    public RecommendationWeight updateSimilarityWithRecommendationWeight(int sourceId, int targetId) {
        RecommendationWeight source = ensureEmbedding(getById(sourceId));
        RecommendationWeight target = ensureEmbedding(getById(targetId));

        List<Double> sourceEmbedding = openAiEmbeddingService.fromJson(source.getEmbeddingVector());
        List<Double> targetEmbedding = openAiEmbeddingService.fromJson(target.getEmbeddingVector());
        source.setSimilarity(openAiEmbeddingService.cosineSimilarity(sourceEmbedding, targetEmbedding));
        return recommendationWeightRepository.save(source);
    }

    private RecommendationWeight ensureEmbedding(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.getEmbeddingVector() == null || recommendationWeight.getEmbeddingVector().isBlank()) {
            return updateEmbedding(recommendationWeight.getId());
        }
        return recommendationWeight;
    }

    private String getEmbeddingText(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.getEmbeddingText() != null && !recommendationWeight.getEmbeddingText().isBlank()) {
            return recommendationWeight.getEmbeddingText();
        }
        return String.join(" ",
                nullToBlank(getLink(recommendationWeight)),
                nullToBlank(getParaStatus(recommendationWeight)),
                nullToBlank(getUserName(recommendationWeight))
        ).trim();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private void validateOwnerAndBookmark(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.getUser() == null || recommendationWeight.getUser().getId() == 0) {
            throw new IllegalArgumentException("추천 가중치는 user.id가 필요합니다.");
        }
        if (recommendationWeight.getBookmark() == null || recommendationWeight.getBookmark().getId() == 0) {
            throw new IllegalArgumentException("추천 가중치는 bookmark.id가 필요합니다.");
        }
    }

    private String getLink(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.getBookmark() != null) {
            return recommendationWeight.getBookmark().getLink();
        }
        return "";
    }

    private String getParaStatus(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.getBookmark() != null) {
            return recommendationWeight.getBookmark().getPARAStatus();
        }
        return "";
    }

    private String getUserName(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.getUser() != null) {
            return recommendationWeight.getUser().getUserName();
        }
        return "";
    }

    private List<Double> buildUserInterestVector(List<RecommendationWeight> recommendationWeights) {
        List<Double> interestVector = new ArrayList<>();
        double totalWeight = 0;

        for (RecommendationWeight recommendationWeight : recommendationWeights) {
            if (recommendationWeight.isSnooze()) {
                continue;
            }

            List<Double> embedding = openAiEmbeddingService.fromJson(recommendationWeight.getEmbeddingVector());
            double profileWeight = calculateProfileWeight(recommendationWeight);
            if (profileWeight <= 0) {
                continue;
            }

            if (interestVector.isEmpty()) {
                for (int i = 0; i < embedding.size(); i++) {
                    interestVector.add(0.0);
                }
            }
            if (interestVector.size() != embedding.size()) {
                throw new IllegalArgumentException("유저 관심 벡터를 만들 수 없습니다. 임베딩 벡터 길이가 다릅니다.");
            }

            for (int i = 0; i < embedding.size(); i++) {
                interestVector.set(i, interestVector.get(i) + embedding.get(i) * profileWeight);
            }
            totalWeight += profileWeight;
        }

        if (interestVector.isEmpty() || totalWeight == 0) {
            throw new IllegalArgumentException("유저 관심 벡터를 만들 추천 가중치가 없습니다.");
        }

        for (int i = 0; i < interestVector.size(); i++) {
            interestVector.set(i, interestVector.get(i) / totalWeight);
        }
        return interestVector;
    }

    private double calculateProfileWeight(RecommendationWeight recommendationWeight) {
        double importanceScore = clamp(recommendationWeight.getImportance(), 0, 1);
        double frequencyScore = normalizeFrequency(recommendationWeight.getFrequency());
        double recencyScore = calculateRecencyScore(recommendationWeight.getLastUpdate());
        double profileWeight = 0.4 * importanceScore + 0.3 * frequencyScore + 0.3 * recencyScore;
        return profileWeight > 0 ? profileWeight : 0.1;
    }

    private double calculateWeightValue(RecommendationWeight recommendationWeight) {
        if (recommendationWeight.isSnooze()) {
            return 0;
        }

        double importanceScore = clamp(recommendationWeight.getImportance(), 0, 1);
        double similarityScore = clamp(recommendationWeight.getSimilarity(), 0, 1);
        double frequencyScore = normalizeFrequency(recommendationWeight.getFrequency());
        double recencyScore = calculateRecencyScore(recommendationWeight.getLastUpdate());
        double paraMultiplier = getParaStatusMultiplier(getParaStatus(recommendationWeight));

        double score = paraMultiplier * (
                0.35 * importanceScore
                        + 0.25 * similarityScore
                        + 0.20 * frequencyScore
                        + 0.20 * recencyScore
        );
        return Math.round(score * 10000.0) / 10000.0;
    }

    private double normalizeFrequency(int frequency) {
        if (frequency <= 0) {
            return 0;
        }
        return clamp(Math.log1p(frequency) / Math.log1p(30), 0, 1);
    }

    private double calculateRecencyScore(LocalDateTime lastUpdate) {
        if (lastUpdate == null) {
            return 0;
        }
        long ageSeconds = java.time.Duration.between(lastUpdate, LocalDateTime.now()).getSeconds();
        double ageDays = Math.max(0, ageSeconds / 86400.0);
        return Math.pow(0.5, ageDays / RECENCY_HALF_LIFE_DAYS);
    }

    private double getParaStatusMultiplier(String paraStatus) {
        if (paraStatus == null) {
            return 0.75;
        }
        return switch (paraStatus.strip().toLowerCase()) {
            case "project", "projects" -> 1.0;
            case "area", "areas" -> 0.85;
            case "resource", "resources" -> 0.65;
            case "archive", "archives" -> 0.35;
            default -> 0.75;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private RecommendationWeight updateFields(RecommendationWeight recommendationWeight, RecommendationWeight request) {
        recommendationWeight.setName(request.getName());
        recommendationWeight.setWeightValue(request.getWeightValue());
        recommendationWeight.setUser(request.getUser());
        recommendationWeight.setSnooze(request.isSnooze());
        recommendationWeight.setFrequency(request.getFrequency());
        recommendationWeight.setLastUpdate(request.getLastUpdate());
        recommendationWeight.setImportance(request.getImportance());
        recommendationWeight.setSimilarity(request.getSimilarity());
        recommendationWeight.setEmbeddingText(request.getEmbeddingText());
        recommendationWeight.setBookmark(request.getBookmark());
        return recommendationWeight;
    }
}
