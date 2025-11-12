package kr.co.direa.backoffice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import kr.co.direa.backoffice.dto.OpenStackInstanceDto;
import kr.co.direa.backoffice.dto.OpenStackInstanceDto.SecurityGroupRuleDto;
import kr.co.direa.backoffice.exception.CustomException;
import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PreDestroy;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class OpenStackService {

    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(1);
    private static final Duration TOKEN_DEFAULT_TTL = Duration.ofHours(1);

    private final RestTemplate restTemplate;
    private final Object tokenLock = new Object();
    private final ScheduledExecutorService tokenRefreshScheduler;
    private volatile ScheduledFuture<?> scheduledRefresh;
    private volatile CachedToken cachedToken;

    @Value("${openstack.auth.url}")
    private String authUrl;

    @Value("${openstack.auth.username}")
    private String username;

    @Value("${openstack.auth.password}")
    private String password;

    @Value("${openstack.auth.domain}")
    private String domain;

    @Value("${openstack.auth.project.id}")
    private String projectId;

    @Value("${openstack.neutron.url}")
    private String neutronUrl;

    @Value("${openstack.nova.url}")
    private String novaUrl;

    @Value("${openstack.glance.url}")
    private String glanceUrl;

    public OpenStackService(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder
                .requestFactory(this::createTrustAllRequestFactory)
                .build();
    this.tokenRefreshScheduler = Executors.newSingleThreadScheduledExecutor(buildSchedulerThreadFactory());
        log.warn("OpenStack RestTemplate skips SSL certificate validation. Trusted-network use only.");
    }

    private ThreadFactory buildSchedulerThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "openstack-token-refresh");
            thread.setDaemon(true);
            return thread;
        };
    }

    @SuppressWarnings("deprecation")
    private HttpComponentsClientHttpRequestFactory createTrustAllRequestFactory() {
        try {
            var sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                    .build();

            SSLConnectionSocketFactory socketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();

            var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(socketFactory)
                    .build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .evictExpiredConnections()
                    .build();
            return new HttpComponentsClientHttpRequestFactory(httpClient);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize trust-all SSL context", exception);
        }
    }

    public OpenStackInstanceDto getInstanceByFloatingIp(String floatingIp) {
        try {
            return fetchInstanceWithTokenRetry(floatingIp);
        } catch (CustomException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to fetch instance for floating IP {}", floatingIp, ex);
            throw new CustomException(CustomErrorCode.OPENSTACK_REQUEST_FAILED, ex.getMessage(), ex);
        }
    }

    private OpenStackInstanceDto fetchInstanceWithTokenRetry(String floatingIp) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = getCachedToken();
            try {
                log.info("Requesting OpenStack instance by floating IP: {}", floatingIp);
                String portId = findPortId(token, floatingIp);
                PortDetailResponse.Port port = findPortDetail(token, portId);

                String instanceId = Optional.ofNullable(port.getDeviceId())
                        .filter(value -> !value.isBlank())
                        .orElseThrow(() -> new CustomException(
                                CustomErrorCode.OPENSTACK_INSTANCE_NOT_FOUND,
                                "플로팅 IP와 연결된 인스턴스를 찾을 수 없습니다."));

                List<String> securityGroupIds = Optional.ofNullable(port.getSecurityGroups())
                        .orElseGet(Collections::emptyList);
                String privateIp = Optional.ofNullable(port.getFixedIps())
                        .orElseGet(Collections::emptyList)
                        .stream()
                        .map(PortDetailResponse.FixedIp::getIpAddress)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(null);

                ServerResponse.Server server = findServerDetail(token, instanceId);
                String flavorName = resolveFlavorName(token, Optional.ofNullable(server.getFlavor())
                        .map(ServerResponse.FlavorRef::getId)
                        .orElse(null));
                String imageName = resolveImageName(token, Optional.ofNullable(server.getImage())
                        .map(ServerResponse.ImageRef::getId)
                        .orElse(null));
                List<SecurityGroupRuleDto> securityGroups = resolveSecurityGroups(token, securityGroupIds);

                return OpenStackInstanceDto.builder()
                        .id(server.getId())
                        .name(server.getName())
                        .status(server.getStatus())
                        .flavor(flavorName)
                        .image(imageName)
                        .host(server.getHypervisorHostname())
                        .privateIp(privateIp)
                        .floatingIp(floatingIp)
                        .internalInstanceName(server.getInstanceName())
                        .keyName(server.getKeyName())
                        .securityGroups(securityGroups)
                        .build();
            } catch (HttpClientErrorException.Unauthorized unauthorized) {
                invalidateToken(token);
                if (attempt == 0) {
                    log.warn("OpenStack token expired. Refreshing and retrying.");
                    continue;
                }
                throw new CustomException(CustomErrorCode.OPENSTACK_REQUEST_FAILED, "OpenStack 토큰이 만료되었습니다.", unauthorized);
            }
        }
        throw new CustomException(CustomErrorCode.OPENSTACK_REQUEST_FAILED, "OpenStack 토큰 재발급 후에도 요청에 실패했습니다.");
    }

    private String getCachedToken() {
        CachedToken snapshot = cachedToken;
        if (snapshot != null && !snapshot.isExpired()) {
            return snapshot.value;
        }
        synchronized (tokenLock) {
            snapshot = cachedToken;
            if (snapshot == null || snapshot.isExpired()) {
                snapshot = requestNewToken();
                cachedToken = snapshot;
                scheduleTokenRefresh(snapshot);
            }
            return snapshot.value;
        }
    }

    private void invalidateToken(String token) {
        if (token == null) {
            return;
        }
        CachedToken snapshot = cachedToken;
        if (snapshot == null || !token.equals(snapshot.value)) {
            return;
        }
        synchronized (tokenLock) {
            if (cachedToken != null && token.equals(cachedToken.value)) {
                cachedToken = null;
                cancelScheduledRefresh();
            }
        }
    }

    private CachedToken requestNewToken() {
        String url = authUrl + "/v3/auth/tokens";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = buildAuthPayload();

        ResponseEntity<AuthTokenResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                AuthTokenResponse.class);

        String tokenValue = response.getHeaders().getFirst("X-Subject-Token");
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new CustomException(CustomErrorCode.OPENSTACK_REQUEST_FAILED, "OpenStack 토큰 발급에 실패했습니다.");
        }
        Instant expiresAt = Optional.ofNullable(response.getBody())
                .map(AuthTokenResponse::getToken)
                .map(AuthTokenResponse.Token::getExpiresAt)
                .map(this::parseExpiresAt)
                .orElse(null);

        if (expiresAt == null) {
            expiresAt = Instant.now().plus(TOKEN_DEFAULT_TTL);
        }

        CachedToken token = new CachedToken(tokenValue, expiresAt);
        log.debug("Issued new OpenStack token expiring at {}", expiresAt);
        return token;
    }

    private Instant parseExpiresAt(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(expiresAt).toInstant();
        } catch (Exception ex) {
            log.warn("Failed to parse OpenStack token expiration: {}", expiresAt, ex);
            return null;
        }
    }

    private void scheduleTokenRefresh(CachedToken token) {
        cancelScheduledRefresh();
        if (token == null || token.expiresAt == null) {
            return;
        }
        Instant refreshInstant = token.expiresAt.minus(TOKEN_REFRESH_MARGIN);
        long delayMillis = Duration.between(Instant.now(), refreshInstant).toMillis();
        if (delayMillis <= 0) {
            tokenRefreshScheduler.execute(this::refreshTokenSilently);
        } else {
            scheduledRefresh = tokenRefreshScheduler.schedule(this::refreshTokenSilently, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelScheduledRefresh() {
        ScheduledFuture<?> future = scheduledRefresh;
        if (future != null) {
            future.cancel(false);
            scheduledRefresh = null;
        }
    }

    private void refreshTokenSilently() {
        synchronized (tokenLock) {
            scheduledRefresh = null;
            try {
                CachedToken refreshed = requestNewToken();
                cachedToken = refreshed;
                scheduleTokenRefresh(refreshed);
                log.debug("Proactively refreshed OpenStack token");
            } catch (Exception ex) {
                cachedToken = null;
                log.warn("Failed to proactively refresh OpenStack token. It will be refreshed on demand.", ex);
            }
        }
    }

    @PreDestroy
    private void shutdownScheduler() {
        synchronized (tokenLock) {
            cancelScheduledRefresh();
        }
        tokenRefreshScheduler.shutdownNow();
    }

    private Map<String, Object> buildAuthPayload() {
        Map<String, Object> user = new HashMap<>();
        user.put("name", username);
        user.put("password", password);
        user.put("domain", Map.of("name", domain));

        Map<String, Object> passwordCredentials = new HashMap<>();
        passwordCredentials.put("user", user);

        Map<String, Object> identity = new HashMap<>();
        identity.put("methods", List.of("password"));
        identity.put("password", passwordCredentials);

        Map<String, Object> scope = Map.of("project", Map.of("id", projectId));

        Map<String, Object> auth = new HashMap<>();
        auth.put("identity", identity);
        auth.put("scope", scope);

        return Map.of("auth", auth);
    }

    private String findPortId(String token, String floatingIp) {
    String url = UriComponentsBuilder.fromUriString(neutronUrl)
                .path("/v2.0/floatingips")
                .queryParam("floating_ip_address", floatingIp)
                .toUriString();

        HttpHeaders headers = createAuthHeaders(token);
        ResponseEntity<FloatingIpResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                FloatingIpResponse.class);

        return Optional.ofNullable(response.getBody())
                .map(FloatingIpResponse::getFloatingIps)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(item -> floatingIp.equals(item.getFloatingIpAddress()))
                .map(FloatingIpResponse.FloatingIp::getPortId)
                .filter(port -> port != null && !port.isBlank())
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        CustomErrorCode.OPENSTACK_INSTANCE_NOT_FOUND,
                        "연결된 포트를 찾을 수 없습니다."));
    }

    private PortDetailResponse.Port findPortDetail(String token, String portId) {
        String url = neutronUrl + "/v2.0/ports/" + portId;
        HttpHeaders headers = createAuthHeaders(token);

        ResponseEntity<PortDetailResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PortDetailResponse.class);

        return Optional.ofNullable(response.getBody())
                .map(PortDetailResponse::getPort)
                .orElseThrow(() -> new CustomException(
                        CustomErrorCode.OPENSTACK_REQUEST_FAILED,
                        "포트 상세 정보를 확인할 수 없습니다."));
    }

    private ServerResponse.Server findServerDetail(String token, String instanceId) {
        String url = novaUrl + "/v2.1/servers/" + instanceId;
        HttpHeaders headers = createAuthHeaders(token);

        ResponseEntity<ServerResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ServerResponse.class);

        return Optional.ofNullable(response.getBody())
                .map(ServerResponse::getServer)
                .orElseThrow(() -> new CustomException(
                        CustomErrorCode.OPENSTACK_INSTANCE_NOT_FOUND,
                        "인스턴스 상세 정보를 확인할 수 없습니다."));
    }

    private String resolveFlavorName(String token, String flavorId) {
        if (flavorId == null || flavorId.isBlank()) {
            return "N/A";
        }

        String url = novaUrl + "/v2.1/flavors/" + flavorId;
        HttpHeaders headers = createAuthHeaders(token);

        try {
            ResponseEntity<FlavorResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    FlavorResponse.class);
            return Optional.ofNullable(response.getBody())
                    .map(FlavorResponse::getFlavor)
                    .map(FlavorResponse.Flavor::getName)
                    .orElse(flavorId);
        } catch (Exception ex) {
            log.warn("Failed to resolve flavor name: {}", flavorId, ex);
            return flavorId;
        }
    }

    private String resolveImageName(String token, String imageId) {
        if (imageId == null || imageId.isBlank()) {
            return "N/A";
        }

        String url = glanceUrl + "/v2/images/" + imageId;
        HttpHeaders headers = createAuthHeaders(token);

        try {
            ResponseEntity<ImageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    ImageResponse.class);
            return Optional.ofNullable(response.getBody())
                    .map(ImageResponse::getName)
                    .orElse(imageId);
        } catch (Exception ex) {
            log.warn("Failed to resolve image name: {}", imageId, ex);
            return imageId;
        }
    }

    private List<SecurityGroupRuleDto> resolveSecurityGroups(String token, List<String> securityGroupIds) {
        if (securityGroupIds == null || securityGroupIds.isEmpty()) {
            return Collections.emptyList();
        }

        String query = securityGroupIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> "id=" + id)
                .collect(Collectors.joining("&"));

        if (query.isBlank()) {
            return Collections.emptyList();
        }

        String url = neutronUrl + "/v2.0/security-groups?" + query;
        HttpHeaders headers = createAuthHeaders(token);

        try {
            ResponseEntity<SecurityGroupResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    SecurityGroupResponse.class);

            List<SecurityGroupResponse.SecurityGroup> securityGroups = Optional.ofNullable(response.getBody())
                    .map(SecurityGroupResponse::getSecurityGroups)
                    .orElseGet(Collections::emptyList);

            Map<String, String> securityGroupNameMap = securityGroups.stream()
                    .collect(Collectors.toMap(SecurityGroupResponse.SecurityGroup::getId,
                            SecurityGroupResponse.SecurityGroup::getName));

            List<SecurityGroupRuleDto> results = new ArrayList<>();

            for (SecurityGroupResponse.SecurityGroup group : securityGroups) {
                List<SecurityGroupResponse.SecurityGroupRule> rules = Optional.ofNullable(group.getSecurityGroupRules())
                        .orElse(Collections.emptyList());

                for (SecurityGroupResponse.SecurityGroupRule rule : rules) {
                    String remoteInfo = buildRemoteInfo(rule, securityGroupNameMap);
                    results.add(SecurityGroupRuleDto.builder()
                            .key(generateSecurityGroupKey(group.getName(), rule))
                            .groupName(group.getName())
                            .direction(rule.getDirection())
                            .protocol(Optional.ofNullable(rule.getProtocol()).orElse("any"))
                            .portRange(buildPortRange(rule.getPortRangeMin(), rule.getPortRangeMax()))
                            .remote(remoteInfo)
                            .ethertype(formatEthertype(rule.getEthertype()))
                            .build());
                }
            }

            results.sort(Comparator.comparing(SecurityGroupRuleDto::getGroupName, Comparator.nullsLast(String::compareTo))
                    .thenComparing(SecurityGroupRuleDto::getDirection, Comparator.nullsLast(String::compareTo))
                    .thenComparing(SecurityGroupRuleDto::getProtocol, Comparator.nullsLast(String::compareTo)));

            return results;
        } catch (Exception ex) {
            log.warn("Failed to load security groups: {}", securityGroupIds, ex);
            return Collections.emptyList();
        }
    }

    private String buildPortRange(Integer min, Integer max) {
        if (min == null && max == null) {
            return "any";
        }
        if (min != null && max != null) {
            if (min.equals(max)) {
                return String.valueOf(min);
            }
            return min + "-" + max;
        }
        return "any";
    }

    private String formatEthertype(String ethertype) {
        if (ethertype == null || ethertype.isBlank()) {
            return "Unknown";
        }
        String lower = ethertype.toLowerCase();
        return switch (lower) {
            case "ipv4" -> "IPv4";
            case "ipv6" -> "IPv6";
            default -> ethertype;
        };
    }

    private String buildRemoteInfo(SecurityGroupResponse.SecurityGroupRule rule, Map<String, String> nameMap) {
        String remoteIpPrefix = rule.getRemoteIpPrefix();
        if (remoteIpPrefix != null && !remoteIpPrefix.isBlank()) {
            return remoteIpPrefix;
        }

        String remoteGroupId = rule.getRemoteGroupId();
        if (remoteGroupId != null && !remoteGroupId.isBlank()) {
            String groupName = nameMap.get(remoteGroupId);
            if (groupName != null && !groupName.isBlank()) {
                return "보안그룹: " + groupName + " (" + remoteGroupId + ")";
            }
            return "보안그룹: " + remoteGroupId;
        }

        return "any";
    }

    private String generateSecurityGroupKey(String groupName, SecurityGroupResponse.SecurityGroupRule rule) {
        String base = Optional.ofNullable(groupName).orElse("security-group");
        String direction = Optional.ofNullable(rule.getDirection()).orElse("any");
        String protocol = Optional.ofNullable(rule.getProtocol()).orElse("any");
        return base + '-' + direction + '-' + protocol + '-' + UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static final class CachedToken {
        private final String value;
        private final Instant expiresAt;

        private CachedToken(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            if (value == null) {
                return true;
            }
            if (expiresAt == null) {
                return false;
            }
            return Instant.now().isAfter(expiresAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AuthTokenResponse {
        private Token token;

        public Token getToken() {
            return token;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Token {
            @JsonProperty("expires_at")
            private String expiresAt;

            public String getExpiresAt() {
                return expiresAt;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FloatingIpResponse {
        @JsonProperty("floatingips")
        private List<FloatingIp> floatingIps;

        public List<FloatingIp> getFloatingIps() {
            return floatingIps;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class FloatingIp {
            @JsonProperty("floating_ip_address")
            private String floatingIpAddress;

            @JsonProperty("port_id")
            private String portId;

            public String getFloatingIpAddress() {
                return floatingIpAddress;
            }

            public String getPortId() {
                return portId;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PortDetailResponse {
        @JsonProperty("port")
        private Port port;

        public Port getPort() {
            return port;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Port {
            @JsonProperty("device_id")
            private String deviceId;

            @JsonProperty("security_groups")
            private List<String> securityGroups;

            @JsonProperty("fixed_ips")
            private List<FixedIp> fixedIps;

            public String getDeviceId() {
                return deviceId;
            }

            public List<String> getSecurityGroups() {
                return securityGroups;
            }

            public List<FixedIp> getFixedIps() {
                return fixedIps;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class FixedIp {
            @JsonProperty("ip_address")
            private String ipAddress;

            public String getIpAddress() {
                return ipAddress;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ServerResponse {
        @JsonProperty("server")
        private Server server;

        public Server getServer() {
            return server;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Server {
            private String id;
            private String name;
            private String status;
            private FlavorRef flavor;
            private ImageRef image;

            @JsonProperty("OS-EXT-SRV-ATTR:hypervisor_hostname")
            private String hypervisorHostname;

            @JsonProperty("OS-EXT-SRV-ATTR:instance_name")
            private String instanceName;

            @JsonProperty("key_name")
            private String keyName;

            public String getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getStatus() {
                return status;
            }

            public FlavorRef getFlavor() {
                return flavor;
            }

            public ImageRef getImage() {
                return image;
            }

            public String getHypervisorHostname() {
                return hypervisorHostname;
            }

            public String getInstanceName() {
                return instanceName;
            }

            public String getKeyName() {
                return keyName;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class FlavorRef {
            private String id;

            public String getId() {
                return id;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class ImageRef {
            private String id;

            public String getId() {
                return id;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlavorResponse {
        @JsonProperty("flavor")
        private Flavor flavor;

        public Flavor getFlavor() {
            return flavor;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Flavor {
            private String id;
            private String name;

            @SuppressWarnings("unused")
            public String getId() {
                return id;
            }

            public String getName() {
                return name;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ImageResponse {
        private String id;
        private String name;

        @SuppressWarnings("unused")
        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SecurityGroupResponse {
        @JsonProperty("security_groups")
        private List<SecurityGroup> securityGroups;

        public List<SecurityGroup> getSecurityGroups() {
            return securityGroups;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class SecurityGroup {
            private String id;
            private String name;

            @JsonProperty("security_group_rules")
            private List<SecurityGroupRule> securityGroupRules;

            public String getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public List<SecurityGroupRule> getSecurityGroupRules() {
                return securityGroupRules;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class SecurityGroupRule {
            private String id;
            private String direction;
            private String protocol;

            @JsonProperty("port_range_min")
            private Integer portRangeMin;

            @JsonProperty("port_range_max")
            private Integer portRangeMax;

            @JsonProperty("remote_ip_prefix")
            private String remoteIpPrefix;

            @JsonProperty("remote_group_id")
            private String remoteGroupId;

            private String ethertype;

            @SuppressWarnings("unused")
            public String getId() {
                return id;
            }

            public String getDirection() {
                return direction;
            }

            public String getProtocol() {
                return protocol;
            }

            public Integer getPortRangeMin() {
                return portRangeMin;
            }

            public Integer getPortRangeMax() {
                return portRangeMax;
            }

            public String getRemoteIpPrefix() {
                return remoteIpPrefix;
            }

            public String getRemoteGroupId() {
                return remoteGroupId;
            }

            public String getEthertype() {
                return ethertype;
            }
        }
    }
}
