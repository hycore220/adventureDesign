package com.example.spring_boot_1.RecommendationData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpenAiEmbeddingService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String embeddingModel;

    public OpenAiEmbeddingService(
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.embedding-model:text-embedding-3-small}") String embeddingModel
    ) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
    }

    public List<Double> createEmbedding(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY 환경변수가 필요합니다.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩할 텍스트가 비어 있습니다.");
        }

        String response = restClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(Map.of(
                        "model", embeddingModel,
                        "input", text,
                        "encoding_format", "float"
                ))
                .retrieve()
                .body(String.class);

        return parseEmbedding(response);
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String toJson(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 벡터를 JSON으로 변환하지 못했습니다.", e);
        }
    }

    public List<Double> fromJson(String embeddingJson) {
        try {
            JsonNode root = objectMapper.readTree(embeddingJson);
            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : root) {
                embedding.add(value.doubleValue());
            }
            return embedding;
        } catch (Exception e) {
            throw new IllegalStateException("저장된 임베딩 벡터를 읽지 못했습니다.", e);
        }
    }

    public double cosineSimilarity(List<Double> first, List<Double> second) {
        if (first.size() != second.size()) {
            throw new IllegalArgumentException("임베딩 벡터 길이가 다릅니다.");
        }

        double dotProduct = 0;
        double firstNorm = 0;
        double secondNorm = 0;

        for (int i = 0; i < first.size(); i++) {
            double firstValue = first.get(i);
            double secondValue = second.get(i);
            dotProduct += firstValue * secondValue;
            firstNorm += firstValue * firstValue;
            secondNorm += secondValue * secondValue;
        }

        if (firstNorm == 0 || secondNorm == 0) {
            return 0;
        }

        return dotProduct / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
    }

    private List<Double> parseEmbedding(String response) {
        try {
            JsonNode embeddingNode = objectMapper.readTree(response)
                    .get("data")
                    .get(0)
                    .get("embedding");

            List<Double> embedding = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                embedding.add(value.doubleValue());
            }
            return embedding;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAI 임베딩 응답을 읽지 못했습니다.", e);
        }
    }
}
