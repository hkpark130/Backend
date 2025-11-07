package kr.co.direa.backoffice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Approval-related configuration values loaded from application properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.approval")
public class ApprovalProperties {
    private List<Stage> defaultApprovers = new ArrayList<>();

    @Data
    public static class Stage {
        private int stage;
        private String label;
        private String username;
        private UUID keycloakId;
        private String displayName;
        private String email;
        private boolean locked = true;
    }
}
