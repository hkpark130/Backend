package kr.co.direa.backoffice.service;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.function.Function;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.dto.PageResponse;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.DeviceApprovalDetailRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.vo.AdminDeviceSearchRequest;
import kr.co.direa.backoffice.vo.DeviceSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DevicesRepository devicesRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DeviceApprovalDetailRepository deviceApprovalDetailRepository;
    private final CommonLookupService commonLookupService;

    private static final Pattern OPERATOR_SUFFIX_PATTERN = Pattern.compile("\\(처리자: (?<username>.+?)\\)$");

    // Bulk 등록 메서드
    @Transactional
    public void bulkRegisterDevices(List<DeviceDto> deviceDtoList) {
        for (DeviceDto dto : deviceDtoList) {
            Devices device = new Devices();
            device.setId(dto.getId());
            // categoryName -> categoryId
            commonLookupService.findCategoryByName(dto.getCategoryName())
                    .ifPresent(device::setCategoryId);
            // manageDepName -> manageDep
            commonLookupService.findDepartmentByName(dto.getManageDepName())
                    .ifPresent(device::setManageDep);
            // projectName -> projectId
            commonLookupService.findProjectByName(dto.getProjectName())
                    .ifPresent(device::setProjectId);
            device.setStatus(dto.getStatus());
            device.setPurpose(dto.getPurpose());
            device.setSpec(dto.getSpec());
            device.setPrice(dto.getPrice());
            device.setModel(dto.getModel());
            device.setCompany(dto.getCompany());
            device.setSn(dto.getSn());
            device.setIsUsable(Boolean.valueOf(String.valueOf(dto.getIsUsable())));
            device.setPurchaseDate(dto.getPurchaseDate());
            device.setDescription(dto.getDescription());
            device.setAdminDescription(dto.getAdminDescription());
            // username -> userUuid (Keycloak)
            commonLookupService.resolveKeycloakUserIdByUsername(dto.getUsername())
                    .ifPresent(uuidStr -> {
                        try {
                            device.setUserUuid(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {}
                    });
            // (선택) 기존 Users 연관 유지가 필요하면 아래 주석 해제
            // commonLookupService.findUserByUsername(dto.getUsername())
            //         .ifPresent(device::setUserId);
            devicesRepository.save(device);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceDto> findAvailableDevices(DeviceSearchRequest request) {
        List<DeviceDto> baseList = loadAvailableDeviceDtos();

        Set<String> categorySet = new HashSet<>();
        Set<String> purposeSet = new HashSet<>();
        Map<String, Long> categoryCountMap = new HashMap<>();
        Map<String, Long> purposeCountMap = new HashMap<>();
        baseList.forEach(dto -> {
            if (dto.getCategoryName() != null && !dto.getCategoryName().isBlank()) {
                categorySet.add(dto.getCategoryName());
                categoryCountMap.merge(dto.getCategoryName(), 1L, Long::sum);
            }
            if (dto.getPurpose() != null && !dto.getPurpose().isBlank()) {
                purposeSet.add(dto.getPurpose());
                purposeCountMap.merge(dto.getPurpose(), 1L, Long::sum);
            }
        });

        String filterField = normalizeDeviceFilterField(request.filterField());
        String keyword = normalizeKeyword(request.keyword());
        String chip = normalizeChipValue(request.chipValue());

        List<DeviceDto> filtered = baseList.stream()
                .filter(dto -> matchesDeviceChip(dto, filterField, chip))
                .filter(dto -> matchesDeviceKeyword(dto, filterField, keyword))
                .collect(Collectors.toList());

        int size = clampSize(request.size());
        int totalElements = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / size));
        int page = clampPage(request.page(), totalPages, totalElements);
        int fromIndex = Math.min((page - 1) * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<DeviceDto> content = filtered.subList(fromIndex, toIndex);

        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("categories", categorySet.stream().sorted(collator).toList());
        metadata.put("purposes", purposeSet.stream().sorted(collator).toList());
        metadata.put("categoryCounts", categoryCountMap);
        metadata.put("purposeCounts", purposeCountMap);

        return PageResponse.of(content, page, size, totalElements, totalPages, metadata);
    }

    private List<DeviceDto> loadAvailableDeviceDtos() {
        List<Devices> devices = devicesRepository.findAllWithApprovals();
        return devices.stream()
                .filter(this::isDeviceAvailable)
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
    }

    private String normalizeDeviceFilterField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
        }
        return switch (raw) {
            case "categoryName", "품목" -> "categoryName";
            case "id", "관리번호" -> "id";
            case "purpose", "용도" -> "purpose";
            case "tags", "태그" -> "tags";
            case "all", "전체" -> "all";
            default -> "all";
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUsernameKey(String username) {
        String normalized = normalizeKeyword(username);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeChipValue(String chipValue) {
        if (chipValue == null) {
            return null;
        }
        String trimmed = chipValue.trim();
        if (trimmed.isEmpty() || "ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private boolean matchesDeviceChip(DeviceDto dto, String filterField, String chipValue) {
        if (chipValue == null) {
            return true;
        }
        return switch (filterField) {
            case "categoryName" -> chipValue.equals(dto.getCategoryName());
            case "purpose" -> chipValue.equals(dto.getPurpose());
            default -> true;
        };
    }

    private boolean matchesDeviceKeyword(DeviceDto dto, String filterField, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        switch (filterField) {
            case "all":
                if (containsIgnoreCase(dto.getCategoryName(), lowerKeyword)) {
                    return true;
                }
                if (containsIgnoreCase(dto.getId(), lowerKeyword)) {
                    return true;
                }
                if (containsIgnoreCase(dto.getPurpose(), lowerKeyword)) {
                    return true;
                }
                if (containsIgnoreCase(dto.getDescription(), lowerKeyword)) {
                    return true;
                }
                if (dto.getTags() != null) {
                    for (String tag : dto.getTags()) {
                        if (containsIgnoreCase(tag, lowerKeyword)) {
                            return true;
                        }
                    }
                }
                return false;
            case "tags":
                if (dto.getTags() == null) {
                    return false;
                }
                for (String tag : dto.getTags()) {
                    if (containsIgnoreCase(tag, lowerKeyword)) {
                        return true;
                    }
                }
                return false;
            case "categoryName":
                return containsIgnoreCase(dto.getCategoryName(), lowerKeyword);
            case "id":
                return containsIgnoreCase(dto.getId(), lowerKeyword);
            case "purpose":
                return containsIgnoreCase(dto.getPurpose(), lowerKeyword);
            default:
                return true;
        }
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private int clampPage(int page, int totalPages, int totalElements) {
        if (totalElements == 0) {
            return 1;
        }
        if (page <= 0) {
            return 1;
        }
        return Math.min(page, Math.max(totalPages, 1));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceDto> findAllDevicesForAdmin(AdminDeviceSearchRequest request) {
        List<Devices> devices = devicesRepository.findAllWithApprovalsAndStatusNot(Constants.DISPOSE_TYPE);
        List<DeviceDto> baseList = devices.stream()
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
        return buildAdminDevicePage(baseList, request, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceDto> findDisposedDevicesForAdmin(AdminDeviceSearchRequest request) {
        List<Devices> devices = devicesRepository.findAllWithApprovalsAndStatus(Constants.DISPOSE_TYPE);
        List<DeviceDto> baseList = devices.stream()
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .toList();
        return buildAdminDevicePage(baseList, request, true);
    }

    private PageResponse<DeviceDto> buildAdminDevicePage(List<DeviceDto> baseList,
                             AdminDeviceSearchRequest request,
                             boolean disposedOnly) {
    AdminDeviceSearchRequest safeRequest = request == null
        ? new AdminDeviceSearchRequest(1, 10, "categoryName", null, null, "categoryName", "asc")
        : request;

    String filterField = normalizeAdminFilterField(safeRequest.filterField());
    String keyword = normalizeKeyword(safeRequest.keyword());
    String filterValue = normalizeFilterValue(safeRequest.filterValue());
    String sortField = normalizeAdminSortField(safeRequest.sortField());
    boolean ascending = !"desc".equalsIgnoreCase(safeRequest.sortDirection());

    List<DeviceDto> filtered = baseList.stream()
        .filter(dto -> matchesAdminFilterValue(dto, filterField, filterValue))
        .filter(dto -> matchesAdminKeyword(dto, filterField, keyword))
        .collect(Collectors.toList());

    Comparator<DeviceDto> comparator = buildAdminComparator(sortField, ascending);
    filtered.sort(comparator);

    int size = clampSize(safeRequest.size());
    int totalElements = filtered.size();
    int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / size));
    int page = clampPage(safeRequest.page(), totalPages, totalElements);
    int fromIndex = Math.min((page - 1) * size, totalElements);
    int toIndex = Math.min(fromIndex + size, totalElements);
    List<DeviceDto> content = filtered.subList(fromIndex, toIndex);

    Map<String, Object> metadata = buildAdminMetadata(baseList, disposedOnly);

    return PageResponse.of(content, page, size, totalElements, totalPages, metadata);
    }

    private Map<String, Object> buildAdminMetadata(List<DeviceDto> baseList, boolean disposedOnly) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Map<String, List<String>> filters = new HashMap<>();
        filters.put("categoryName", collectUniqueValues(baseList, DeviceDto::getCategoryName, collator));
        filters.put("username", collectUniqueValues(baseList, this::displayUser, collator));
        filters.put("manageDepName", collectUniqueValues(baseList, DeviceDto::getManageDepName, collator));
        filters.put("projectName", collectUniqueValues(baseList, DeviceDto::getProjectName, collator));
        filters.put("purpose", collectUniqueValues(baseList, DeviceDto::getPurpose, collator));
        filters.put("company", collectUniqueValues(baseList, DeviceDto::getCompany, collator));
        filters.put("status", collectUniqueValues(baseList, DeviceDto::getStatus, collator));
        filters.put("id", List.of());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filters", filters);
        Map<String, Long> statusCounts = baseList.stream()
                .collect(Collectors.groupingBy(dto -> Optional.ofNullable(dto.getStatus()).orElse("UNKNOWN"),
                        Collectors.counting()));
        metadata.put("statusCounts", statusCounts);
        metadata.put("totalRecords", baseList.size());
        metadata.put("disposedOnly", disposedOnly);
        metadata.put("pageSizeOptions", List.of(10, 25, 50));
        return metadata;
    }

    private List<String> collectUniqueValues(List<DeviceDto> source,
                                             Function<DeviceDto, String> extractor,
                                             Collator collator) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new TreeSet<>(collator);
        for (DeviceDto dto : source) {
            String value = normalizeForFilter(extractor.apply(dto));
            if (value != null) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    private String normalizeForFilter(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeFilterValue(String raw) {
        return normalizeForFilter(raw);
    }

    private String normalizeAdminFilterField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "categoryName";
        }
        return switch (raw) {
            case "categoryName", "품목" -> "categoryName";
            case "id", "관리번호" -> "id";
            case "username", "사용자" -> "username";
            case "status", "상태" -> "status";
            case "manageDepName", "관리부서" -> "manageDepName";
            case "projectName", "프로젝트" -> "projectName";
            case "purpose", "용도" -> "purpose";
            case "company", "제조사" -> "company";
            case "model", "모델명" -> "model";
            case "description", "비고" -> "description";
            case "purchaseDate", "구입일자" -> "purchaseDate";
            default -> "categoryName";
        };
    }

    private String normalizeAdminSortField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "categoryName";
        }
        return switch (raw) {
            case "id", "관리번호" -> "id";
            case "username", "사용자" -> "username";
            case "status", "상태" -> "status";
            case "manageDepName", "관리부서" -> "manageDepName";
            case "projectName", "프로젝트" -> "projectName";
            case "purpose", "용도" -> "purpose";
            case "company", "제조사" -> "company";
            case "model", "모델명" -> "model";
            case "description", "비고" -> "description";
            case "purchaseDate", "구입일자" -> "purchaseDate";
            case "price" -> "price";
            default -> "categoryName";
        };
    }

    private boolean matchesAdminFilterValue(DeviceDto dto, String filterField, String filterValue) {
        if (filterValue == null || filterValue.isBlank()) {
            return true;
        }
        String target = resolveAdminField(dto, filterField);
        return filterValue.equals(target);
    }

    private boolean matchesAdminKeyword(DeviceDto dto, String filterField, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String target = resolveAdminField(dto, filterField);
        if (target == null || target.isBlank()) {
            return false;
        }
        return target.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String resolveAdminField(DeviceDto dto, String field) {
        if (dto == null) {
            return null;
        }
        return switch (field) {
            case "id" -> dto.getId();
            case "username" -> displayUser(dto);
            case "status" -> dto.getStatus();
            case "manageDepName" -> dto.getManageDepName();
            case "projectName" -> dto.getProjectName();
            case "purpose" -> dto.getPurpose();
            case "company" -> dto.getCompany();
            case "model" -> dto.getModel();
            case "description" -> dto.getDescription();
            case "purchaseDate" -> dto.getPurchaseDate() != null ? formatPurchaseDate(dto.getPurchaseDate()) : null;
            default -> dto.getCategoryName();
        };
    }

    private String displayUser(DeviceDto dto) {
        if (dto == null) {
            return null;
        }
        String realUser = normalizeForFilter(dto.getRealUser());
        if (realUser != null) {
            return realUser;
        }
        String username = normalizeForFilter(dto.getUsername());
        if (username != null) {
            return username;
        }
        return normalizeForFilter(dto.getUserEmail());
    }

    private Comparator<DeviceDto> buildAdminComparator(String sortField, boolean ascending) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Comparator<DeviceDto> comparator;
        if ("purchaseDate".equals(sortField)) {
            comparator = Comparator.comparing(dto -> sortableDate(dto.getPurchaseDate()), Comparator.nullsLast(Long::compareTo));
        } else if ("price".equals(sortField)) {
            comparator = Comparator.comparing(DeviceDto::getPrice, Comparator.nullsLast(Long::compareTo));
        } else {
            comparator = Comparator.comparing(dto -> sortableString(resolveAdminField(dto, sortField)), Comparator.nullsLast(collator));
        }

        comparator = comparator.thenComparing(dto -> sortableString(dto.getId()), Comparator.nullsLast(collator));

        return ascending ? comparator : comparator.reversed();
    }

    private String sortableString(String value) {
        return normalizeForFilter(value);
    }

    private Long sortableDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return date.getTime();
    }

    private String formatPurchaseDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString();
    }


    @Transactional(readOnly = true)
    public List<DeviceDto> findDevicesForCurrentUser() {
        UUID userUuid = commonLookupService.currentUserIdFromJwt()
                .flatMap(this::parseUuidSafely)
                .orElse(null);

        String normalizedUsername = commonLookupService.currentUsernameFromJwt()
                .map(this::normalizeKeyword)
                .orElse(null);
        String usernameKey = normalizedUsername != null ? normalizeUsernameKey(normalizedUsername) : null;

        if (userUuid == null && usernameKey == null) {
            return List.of();
        }

        List<Devices> rawDevices = devicesRepository.findAllForUser(userUuid, usernameKey);
        if (rawDevices.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Devices> uniqueDevices = new LinkedHashSet<>(rawDevices);
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Comparator<DeviceDto> comparator = Comparator
                .comparing((DeviceDto dto) -> sortableString(dto.getCategoryName()), Comparator.nullsLast(collator))
                .thenComparing(dto -> sortableString(dto.getId()), Comparator.nullsLast(collator));

        return uniqueDevices.stream()
                .filter(device -> device.getStatus() == null || !Constants.DISPOSE_TYPE.equals(device.getStatus()))
                .filter(device -> ownsDevice(device, usernameKey, userUuid))
                .map(device -> new DeviceDto(device, buildHistory(device.getId())))
                .sorted(comparator)
                .toList();
    }

    @Transactional
    public DeviceDto updateMyDeviceDescription(String deviceId, String description) {
        UUID jwtUserUuid = commonLookupService.currentUserIdFromJwt()
                .flatMap(this::parseUuidSafely)
                .orElse(null);
        String normalizedUsername = commonLookupService.currentUsernameFromJwt()
                .map(this::normalizeKeyword)
                .orElse(null);

        String usernameKey = normalizedUsername != null ? normalizeUsernameKey(normalizedUsername) : null;

        UUID effectiveUuid = jwtUserUuid;
        if (effectiveUuid == null && normalizedUsername != null) {
            effectiveUuid = commonLookupService.resolveKeycloakUserInfoByUsername(normalizedUsername)
                    .map(CommonLookupService.KeycloakUserInfo::id)
                    .orElse(null);
        }

        if (effectiveUuid == null && usernameKey == null) {
            throw new IllegalArgumentException("인증된 사용자 정보를 확인할 수 없습니다.");
        }

        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!ownsDevice(device, usernameKey, effectiveUuid)) {
            throw new IllegalArgumentException("요청한 사용자의 장비가 아닙니다.");
        }

        String normalizedDescription = Optional.ofNullable(description)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);

        device.setDescription(normalizedDescription);
        devicesRepository.save(device);

        return new DeviceDto(device, buildHistory(deviceId));
    }

    private boolean ownsDevice(Devices device, String normalizedUsernameKey, UUID expectedUserUuid) {
        if (device == null || (normalizedUsernameKey == null && expectedUserUuid == null)) {
            return false;
        }

        if (expectedUserUuid != null) {
            UUID deviceUuid = device.getUserUuid();
            if (deviceUuid != null && expectedUserUuid.equals(deviceUuid)) {
                return true;
            }
            UUID externalId = Optional.ofNullable(device.getUserId())
                    .map(Users::getExternalId)
                    .orElse(null);
            if (externalId != null && expectedUserUuid.equals(externalId)) {
                return true;
            }
        }

    String realUserKey = normalizeUsernameKey(device.getRealUser());
    return normalizedUsernameKey != null && normalizedUsernameKey.equals(realUserKey);
    }

    private Optional<UUID> parseUuidSafely(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(source));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }


    public DeviceDto findById(String id) {
        Devices device = devicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        return new DeviceDto(device, buildHistory(id));
    }

    @Transactional
    public DeviceDto disposeDeviceByAdmin(String deviceId, String reason, String operatorOverride) {
        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        String operatorUsername = resolveOperatorUsername(operatorOverride);
        validateOperatorUser(operatorUsername);

        findLatestDetail(device).ifPresent(detail -> {
            ApprovalRequest request = detail.getRequest();
            if (request == null) {
                return;
            }
            if (detail.getAction() == DeviceApprovalAction.DISPOSAL
                    && request.getStatus() != ApprovalStatus.APPROVED) {
                request.markApproved();
                approvalRequestRepository.save(request);
            } else if (request.getStatus() == ApprovalStatus.PENDING
                    || request.getStatus() == ApprovalStatus.IN_PROGRESS) {
                request.markRejected();
                approvalRequestRepository.save(request);
            }
        });

        device.setStatus(Constants.DISPOSE_TYPE);
        device.setIsUsable(Boolean.FALSE);
        device.setUserId(null);
        device.setRealUser(null);
        device.setUserUuid(null);
        devicesRepository.save(device);

        ApprovalRequest disposalRequest = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.APPROVED)
                .title(buildSystemTitle(DeviceApprovalAction.DISPOSAL, device))
                .reason(buildOperatorTaggedReason(
                        Optional.ofNullable(reason).map(String::trim).filter(r -> !r.isEmpty())
                                .orElse("관리자 즉시 폐기 처리"),
                        operatorUsername))
                .build();
        disposalRequest.setSubmittedAt(LocalDateTime.now());
        disposalRequest.setCompletedAt(LocalDateTime.now());

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(DeviceApprovalAction.DISPOSAL);
        detail.setRequestedStatus(Constants.DISPOSE_TYPE);
        detail.setRequestedProject(device.getProjectId());
        detail.setRequestedRealUser(operatorUsername);
        detail.attachTo(disposalRequest);
        device.getApprovalDetails().add(detail);

        approvalRequestRepository.save(disposalRequest);

        return new DeviceDto(device, buildHistory(deviceId));
    }

    @Transactional
    public DeviceDto recoverDeviceByAdmin(String deviceId, String reason, String operatorOverride) {
        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        if (!Constants.DISPOSE_TYPE.equals(device.getStatus())) {
            throw new IllegalStateException("Disposed device만 복구할 수 있습니다.");
        }

        String operatorUsername = resolveOperatorUsername(operatorOverride);
        validateOperatorUser(operatorUsername);

        device.setStatus(Constants.NORMAL_TYPE);
        device.setIsUsable(Boolean.TRUE);
        devicesRepository.save(device);

        ApprovalRequest recoveryRequest = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.APPROVED)
                .title(buildSystemTitle(DeviceApprovalAction.RECOVERY, device))
                .reason(buildOperatorTaggedReason(
                        Optional.ofNullable(reason).map(String::trim).filter(r -> !r.isEmpty())
                                .orElse("관리자 복구 처리"),
                        operatorUsername))
                .build();
        recoveryRequest.setSubmittedAt(LocalDateTime.now());
        recoveryRequest.setCompletedAt(LocalDateTime.now());

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(DeviceApprovalAction.RECOVERY);
        detail.setRequestedStatus(Constants.NORMAL_TYPE);
        detail.setRequestedProject(device.getProjectId());
        detail.setRequestedRealUser(operatorUsername);
        detail.attachTo(recoveryRequest);
        device.getApprovalDetails().add(detail);

        approvalRequestRepository.save(recoveryRequest);

        return new DeviceDto(device, buildHistory(deviceId));
    }

    @Transactional
    public DeviceDto updateDevice(String id, DeviceDto dto) {
        Devices device = devicesRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));

        var categoryOpt = commonLookupService.findCategoryByName(dto.getCategoryName());
        var projectOpt = commonLookupService.findProjectByName(dto.getProjectName());
        var deptOpt = commonLookupService.findDepartmentByName(dto.getManageDepName());

        long price = dto.getPrice() == null ? 0L : dto.getPrice();
        device.update(
                categoryOpt.orElse(null),
                projectOpt.orElse(null),
                deptOpt.orElse(null),
                price,
                dto.getStatus(),
                dto.getPurpose(),
                dto.getDescription(),
                dto.getAdminDescription(),
                dto.getModel(),
                dto.getCompany(),
                dto.getSn(),
                dto.getSpec(),
                dto.getPurchaseDate()
        );

        // 사용자 정보 및 실사용자 반영
        device.setRealUser(dto.getRealUser());
        commonLookupService.findUserByUsername(dto.getUsername()).ifPresent(device::setUserId);
        commonLookupService.resolveKeycloakUserIdByUsername(dto.getUsername()).ifPresent(uuidStr -> {
            try { device.setUserUuid(java.util.UUID.fromString(uuidStr)); } catch (IllegalArgumentException ignore) {}
        });

        devicesRepository.save(device);
        return new DeviceDto(device, buildHistory(id));
    }

    private boolean isDeviceAvailable(Devices device) {
        Optional<DeviceApprovalDetail> latestDetail = findLatestDetail(device);
        if (latestDetail.isEmpty()) {
            return Boolean.TRUE.equals(device.getIsUsable());
        }

        DeviceApprovalDetail detail = latestDetail.get();
        ApprovalRequest request = detail.getRequest();
        DeviceApprovalAction action = detail.getAction();
        ApprovalStatus status = request != null ? request.getStatus() : null;

        boolean returnWaiting = action == DeviceApprovalAction.RETURN &&
                (status == ApprovalStatus.PENDING || status == ApprovalStatus.APPROVED);
        boolean rentalRejected = action == DeviceApprovalAction.RENTAL && status == ApprovalStatus.REJECTED;

        return returnWaiting || rentalRejected || Boolean.TRUE.equals(device.getIsUsable());
    }

    private Optional<DeviceApprovalDetail> findLatestDetail(Devices device) {
        return device.getApprovalDetails().stream()
                .max(Comparator.comparing(
                        detail -> Optional.ofNullable(detail.getRequest())
                                .map(ApprovalRequest::getCreatedDate)
                                .orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private List<Map<String, Object>> buildHistory(String deviceId) {
        List<DeviceApprovalDetail> histories = deviceApprovalDetailRepository.findHistoryByDevice(deviceId);
        List<Map<String, Object>> historyList = new ArrayList<>();
        for (DeviceApprovalDetail detail : histories) {
            Map<String, Object> map = new HashMap<>();
            ApprovalRequest request = detail.getRequest();

            Optional<String> requesterName = Optional.ofNullable(request)
                    .map(ApprovalRequest::getRequesterName)
                    .filter(name -> name != null && !name.isBlank());
            if (requesterName.isEmpty()) {
                requesterName = Optional.ofNullable(request)
                        .map(ApprovalRequest::getRequester)
                        .map(Users::getUsername);
            }

            Optional<String> operatorFromReason = extractOperatorFromReason(
                    Optional.ofNullable(request).map(ApprovalRequest::getReason).orElse(null));

            map.put("username", requesterName.or(() -> operatorFromReason).orElse("알 수 없음"));
            operatorFromReason.ifPresent(value -> map.put("operatorUsername", value));
            map.put("reason", Optional.ofNullable(request).map(ApprovalRequest::getReason).orElse(detail.getMemo()));
            map.put("type", Optional.ofNullable(detail.getAction()).map(DeviceApprovalAction::getDisplayName).orElse(null));
            map.put("projectName", Optional.ofNullable(detail.getRequestedProject())
                    .map(Projects::getName)
                    .orElse(Optional.ofNullable(detail.getDevice())
                            .map(Devices::getProjectId)
                            .map(project -> project != null ? project.getName() : null)
                            .orElse(null)));
            map.put("modifiedDate", Optional.ofNullable(request).map(ApprovalRequest::getModifiedDate).orElse(null));
            historyList.add(map);
        }
        return historyList;
    }

    private String buildOperatorTaggedReason(String baseReason, String operatorUsername) {
        if (operatorUsername == null || operatorUsername.isBlank()) {
            return baseReason;
        }
        String suffix = " (처리자: " + operatorUsername + ")";
        if (baseReason.endsWith(suffix)) {
            return baseReason;
        }
        return baseReason + suffix;
    }

    private String buildSystemTitle(DeviceApprovalAction action, Devices device) {
        String deviceId = device != null ? device.getId() : null;
        if (deviceId == null || deviceId.isBlank()) {
            return "시스템 " + action.getDisplayName();
        }
        return "시스템 " + action.getDisplayName() + " - " + deviceId;
    }

    private String resolveOperatorUsername(String operatorOverride) {
        return commonLookupService.currentUsernameFromJwt()
                .or(() -> Optional.ofNullable(operatorOverride).filter(s -> !s.isBlank()))
                .orElseThrow(() -> new IllegalStateException("인증된 사용자 정보를 찾을 수 없습니다."));
    }

    private void validateOperatorUser(String operatorUsername) {
        boolean operatorIsAdmin = commonLookupService.isAdminUser(operatorUsername);
        if (!operatorIsAdmin) {
            commonLookupService.resolveKeycloakUserIdByUsername(operatorUsername)
                    .orElseThrow(() -> new IllegalArgumentException("Keycloak user not found: " + operatorUsername));
        }
    }

    private Optional<String> extractOperatorFromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = OPERATOR_SUFFIX_PATTERN.matcher(reason.trim());
        if (matcher.find()) {
            String username = matcher.group("username");
            if (username != null && !username.isBlank()) {
                return Optional.of(username.trim());
            }
        }
        return Optional.empty();
    }
}
