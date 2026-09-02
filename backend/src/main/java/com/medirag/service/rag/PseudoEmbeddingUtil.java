package com.medirag.service.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PseudoEmbeddingUtil {

    private PseudoEmbeddingUtil() {
    }

    public static List<Float> embed(String text, int dimensions) {
        float[] vector = new float[dimensions];
        String normalized = normalize(text);

        if (normalized.isEmpty()) {
            return toList(vector);
        }

        for (String token : normalized.split("\\s+")) {
            if (!token.isEmpty()) {
                addToken(vector, token);
            }
        }

        String compact = normalized.replace(" ", "");
        for (int i = 0; i + 2 < compact.length(); i++) {
            addToken(vector, compact.substring(i, i + 3));
        }

        normalizeVector(vector);
        return toList(vector);
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static void addToken(float[] vector, String token) {
        int dim = vector.length;
        int hash = token.hashCode();
        int primary = Math.floorMod(hash, dim);
        int secondary = Math.floorMod(hash * 31, dim);
        vector[primary] += 1.0f;
        vector[secondary] += 0.5f;
    }

    private static void normalizeVector(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
