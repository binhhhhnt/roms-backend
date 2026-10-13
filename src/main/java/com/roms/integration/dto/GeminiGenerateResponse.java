package com.roms.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiGenerateResponse {
    private List<GeminiCandidate> candidates;

    /**
     * Convenience method to extract generated text from the first candidate part.
     *
     * @return generated text or empty string if absent
     */
    public String getFirstText() {
        if (candidates != null && !candidates.isEmpty()) {
            GeminiCandidate firstCandidate = candidates.get(0);
            if (firstCandidate.getContent() != null && firstCandidate.getContent().getParts() != null) {
                List<GeminiPart> parts = firstCandidate.getContent().getParts();
                if (!parts.isEmpty() && parts.get(0).getText() != null) {
                    return parts.get(0).getText();
                }
            }
        }
        return "";
    }
}
