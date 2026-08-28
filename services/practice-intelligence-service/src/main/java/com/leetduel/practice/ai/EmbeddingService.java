package com.leetduel.practice.ai;

import com.leetduel.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final NvidiaClient nvidiaClient;
    private final PracticeRepository practiceRepository;

    public int backfill(int batchSize) {
        if (!nvidiaClient.configured()) {
            throw new NvidiaClient.ProviderException("NVIDIA_API_KEY is not configured");
        }
        int embedded = 0;
        for (PracticeRepository.UnembeddedDocument document : practiceRepository.findUnembeddedDocuments(batchSize)) {
            String text = document.title() + "\n" + document.description() + "\nTags: "
                    + String.join(", ", document.tags());
            List<Double> embedding = nvidiaClient.embed(text, "passage");
            practiceRepository.saveEmbedding(document.problemId(), toVectorLiteral(embedding), "nvidia/nemotron-3-embed-1b");
            embedded++;
        }
        return embedded;
    }

    public String queryVector(String text) {
        return toVectorLiteral(nvidiaClient.embed(text, "query"));
    }

    private String toVectorLiteral(List<Double> values) {
        return values.toString();
    }
}
