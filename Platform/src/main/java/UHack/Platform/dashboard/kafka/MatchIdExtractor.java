package UHack.Platform.dashboard.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class MatchIdExtractor {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MatchIdExtractor() {}

    public static Long extract(String json, String... pathCandidates) {
        try {
            JsonNode root = MAPPER.readTree(json);
            for (String path : pathCandidates) {
                JsonNode node = root;
                for (String part : path.split("\\.")) {
                    if (node == null || node.isMissingNode()) break;
                    node = node.get(part);
                }
                if (node != null && node.canConvertToLong()) {
                    return node.asLong();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
