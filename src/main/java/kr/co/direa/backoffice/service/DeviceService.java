package kr.co.direa.backoffice.service;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Function;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.Categories;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.domain.enums.StepStatus;
import kr.co.direa.backoffice.dto.DeviceDto;
import kr.co.direa.backoffice.dto.PageResponse;
import kr.co.direa.backoffice.exception.CustomException;
import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.DeviceApprovalDetailRepository;
import kr.co.direa.backoffice.repository.DeviceTagRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.repository.DevicesRepository.DeviceCategorySummary;
import kr.co.direa.backoffice.repository.spec.DeviceSpecifications;
import kr.co.direa.backoffice.vo.AdminDeviceSearchRequest;
import kr.co.direa.backoffice.vo.DeviceSearchRequest;
import kr.co.direa.backoffice.vo.MyDeviceSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DevicesRepository devicesRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DeviceApprovalDetailRepository deviceApprovalDetailRepository;
    private final DeviceTagRepository deviceTagRepository;
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
            device.setMacAddress(dto.getMacAddress());
            device.setIsUsable(Boolean.valueOf(String.valueOf(dto.getIsUsable())));
            device.setVatIncluded(dto.getVatIncluded());
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
            device.setRealUser(dto.getRealUser());
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
        List<Devices> devices = devicesRepository.findAllWithDetails();

        List<Devices> availableDevices = devices.stream()
                .filter(this::isDeviceAvailable)
                .toList();

        Map<String, List<Map<String, Object>>> historyMap = buildHistoryMap(availableDevices);

    return availableDevices.stream()
        .map(device -> toDeviceDto(device, historyMap.getOrDefault(device.getId(), Collections.emptyList())))
                .toList();
    }

    private DeviceDto toDeviceDto(Devices device) {
        if (device == null) {
            return null;
        }
        return toDeviceDto(device, buildHistory(device.getId()));
    }

    private DeviceDto toDeviceDto(Devices device, List<Map<String, Object>> history) {
        if (device == null) {
            return null;
        }
        List<Map<String, Object>> safeHistory = history != null ? history : Collections.emptyList();
        DeviceDto dto = new DeviceDto(device, safeHistory);
        enrichDeviceUserProfile(device, dto);
        return dto;
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

    private Map<String, Object> buildEmptyMyDeviceMetadata() {
        return buildMyDeviceMetadata(Collections.emptyList());
    }

    private Map<String, Object> buildMyDeviceMetadata(List<DeviceDto> dtoList) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Set<String> categories = new TreeSet<>(collator);
        Set<String> projects = new TreeSet<>(collator);
        Set<String> departments = new TreeSet<>(collator);

        for (DeviceDto dto : dtoList) {
            Optional.ofNullable(normalizeForFilter(dto.getCategoryName())).ifPresent(categories::add);
            Optional.ofNullable(normalizeForFilter(dto.getProjectName())).ifPresent(projects::add);
            Optional.ofNullable(normalizeForFilter(dto.getManageDepName())).ifPresent(departments::add);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("categories", List.copyOf(categories));
        metadata.put("projects", List.copyOf(projects));
        metadata.put("departments", List.copyOf(departments));
        metadata.put("totalRecords", dtoList.size());
        metadata.put("pageSizeOptions", List.of(7, 10, 25, 50));
        return metadata;
    }

    private String normalizeMyDeviceFilterField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
        }
        return switch (raw) {
            case "categoryName", "품목" -> "categoryName";
            case "id", "관리번호" -> "id";
            case "manageDepName", "관리부서" -> "manageDepName";
            case "projectName", "프로젝트" -> "projectName";
            case "purpose", "용도" -> "purpose";
            case "statusLabel", "진행상태" -> "statusLabel";
            case "displayUser", "사용자" -> "displayUser";
            case "all", "전체" -> "all";
            default -> "all";
        };
    }

    private String normalizeMyDeviceSortField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "categoryName";
        }
        return switch (raw) {
            case "id", "관리번호" -> "id";
            case "displayUser", "사용자" -> "displayUser";
            case "manageDepName", "관리부서" -> "manageDepName";
            case "projectName", "프로젝트" -> "projectName";
            case "purpose", "용도" -> "purpose";
            case "statusLabel", "진행상태" -> "statusLabel";
            default -> "categoryName";
        };
    }

    private boolean matchesMyDeviceChip(DeviceDto dto, String filterField, String chipValue) {
        if (chipValue == null) {
            return true;
        }
        return switch (filterField) {
            case "categoryName" -> chipValue.equals(normalizeForFilter(dto.getCategoryName()));
            case "projectName" -> chipValue.equals(normalizeForFilter(dto.getProjectName()));
            case "manageDepName" -> chipValue.equals(normalizeForFilter(dto.getManageDepName()));
            default -> true;
        };
    }

    private boolean matchesMyDeviceKeyword(DeviceDto dto, String filterField, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowered = keyword.toLowerCase(Locale.ROOT);
        return switch (filterField) {
            case "all" -> Stream.of(
                            dto.getCategoryName(),
                            dto.getId(),
                            dto.getPurpose(),
                            dto.getProjectName(),
                            dto.getManageDepName(),
                            computeDisplayUser(dto),
                            computeStatusLabel(dto),
                            dto.getDescription(),
                            dto.getSpec())
                    .filter(Objects::nonNull)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(value -> value.contains(lowered));
            case "categoryName" -> containsIgnoreCase(dto.getCategoryName(), lowered);
            case "id" -> containsIgnoreCase(dto.getId(), lowered);
            case "manageDepName" -> containsIgnoreCase(dto.getManageDepName(), lowered);
            case "projectName" -> containsIgnoreCase(dto.getProjectName(), lowered);
            case "purpose" -> containsIgnoreCase(dto.getPurpose(), lowered);
            case "displayUser" -> containsIgnoreCase(computeDisplayUser(dto), lowered);
            case "statusLabel" -> containsIgnoreCase(computeStatusLabel(dto), lowered);
            default -> true;
        };
    }

    private Comparator<DeviceDto> buildMyDeviceComparator(String sortField, boolean ascending) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Comparator<DeviceDto> comparator = switch (sortField) {
            case "id" -> Comparator.comparing(dto -> sortableString(dto.getId()), Comparator.nullsLast(collator));
            case "displayUser" -> Comparator.comparing(dto -> sortableString(computeDisplayUser(dto)), Comparator.nullsLast(collator));
            case "manageDepName" -> Comparator.comparing(dto -> sortableString(dto.getManageDepName()), Comparator.nullsLast(collator));
            case "projectName" -> Comparator.comparing(dto -> sortableString(dto.getProjectName()), Comparator.nullsLast(collator));
            case "purpose" -> Comparator.comparing(dto -> sortableString(dto.getPurpose()), Comparator.nullsLast(collator));
            case "statusLabel" -> Comparator.comparing(dto -> sortableString(computeStatusLabel(dto)), Comparator.nullsLast(collator));
            case "categoryName" -> Comparator.comparing(dto -> sortableString(dto.getCategoryName()), Comparator.nullsLast(collator));
            default -> Comparator.comparing(dto -> sortableString(dto.getCategoryName()), Comparator.nullsLast(collator));
        };

        comparator = comparator.thenComparing(dto -> sortableString(dto.getId()), Comparator.nullsLast(collator));
        return ascending ? comparator : comparator.reversed();
    }

    private String computeDisplayUser(DeviceDto dto) {
        String realUser = normalizeForFilter(dto.getRealUser());
        if (realUser != null) {
            return realUser;
        }
        return normalizeForFilter(dto.getUsername());
    }

    private String computeStatusLabel(DeviceDto dto) {
        String approvalInfo = normalizeForFilter(dto.getApprovalInfo());
        String approvalType = normalizeForFilter(dto.getApprovalType());
        if (approvalInfo == null) {
            return normalizeForFilter(dto.getStatus());
        }
        if (approvalType == null || "승인완료".equals(approvalInfo)) {
            return approvalInfo;
        }
        return (approvalType + " " + approvalInfo).trim();
    }

    private Map<String, List<String>> loadTagsByDeviceIds(Collection<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = deviceTagRepository.findTagNamesByDeviceIds(deviceIds);
        Map<String, LinkedHashSet<String>> accumulator = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            String deviceId = row[0] != null ? row[0].toString() : null;
            String tagName = row[1] != null ? row[1].toString() : null;
            if (deviceId == null || tagName == null || tagName.isBlank()) {
                continue;
            }
            accumulator.computeIfAbsent(deviceId, id -> new LinkedHashSet<>()).add(tagName);
        }
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : accumulator.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return result;
    }

    private Map<String, LatestApprovalSnapshot> loadLatestApprovalSnapshots(Collection<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = deviceApprovalDetailRepository.findLatestApprovalSnapshots(deviceIds);
        Map<String, LatestApprovalSnapshot> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 8) {
                continue;
            }
            String deviceId = row[0] != null ? row[0].toString() : null;
            if (deviceId == null || deviceId.isBlank()) {
                continue;
            }
            DeviceApprovalAction action = toDeviceApprovalAction(row[1]);
            ApprovalStatus status = toApprovalStatus(row[2]);
            Long requestId = toLong(row[3]);
            LocalDateTime dueDate = toLocalDateTime(row[4]);
            LocalDateTime createdDate = toLocalDateTime(row[5]);
            String requesterName = toStringSafe(row[6]);
            String requestedRealUser = toStringSafe(row[7]);
            result.put(deviceId, new LatestApprovalSnapshot(deviceId, action, status, requestId, dueDate, createdDate, requesterName, requestedRealUser));
        }
        return result;
    }

    private DeviceDto toMyDeviceDto(Devices device,
                                    LatestApprovalSnapshot snapshot,
                                    List<String> tags) {
        DeviceDto dto = new DeviceDto();
        dto.setId(device.getId());
        dto.setUsername(device.getRealUser());
        dto.setUserEmail(null);
        dto.setUserUuid(device.getUserUuid());
        dto.setRealUser(device.getRealUser());
        dto.setManageDepName(Optional.ofNullable(device.getManageDep()).map(Departments::getName).orElse(null));
        dto.setCategoryName(Optional.ofNullable(device.getCategoryId()).map(Categories::getName).orElse(null));
        Projects project = device.getProjectId();
        dto.setProjectName(Optional.ofNullable(project).map(Projects::getName).orElse(null));
        dto.setProjectCode(Optional.ofNullable(project).map(Projects::getCode).orElse(null));
        dto.setSpec(device.getSpec());
        dto.setPrice(device.getPrice());
        dto.setVatIncluded(device.getVatIncluded());
        dto.setModel(device.getModel());
        dto.setDescription(device.getDescription());
        dto.setAdminDescription(device.getAdminDescription());
        dto.setCompany(device.getCompany());
        dto.setSn(device.getSn());
        dto.setMacAddress(device.getMacAddress());
        dto.setStatus(device.getStatus());
        dto.setIsUsable(device.getIsUsable());
        dto.setPurpose(device.getPurpose());
        dto.setPurchaseDate(device.getPurchaseDate());
        dto.setTags(tags != null ? List.copyOf(tags) : List.of());
        dto.setHistory(Collections.emptyList());
        if (snapshot != null) {
            dto.setApprovalInfo(snapshot.statusDisplayName());
            dto.setApprovalType(snapshot.actionDisplayName());
            dto.setApprovalId(snapshot.requestId());
            dto.setDeadline(snapshot.dueDate());
            if (snapshot.assignsUser()) {
                if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                    dto.setUsername(Optional.ofNullable(snapshot.requesterName()).filter(name -> !name.isBlank())
                            .orElse(snapshot.requestedRealUser()));
                }
                if (dto.getRealUser() == null || dto.getRealUser().isBlank()) {
                    dto.setRealUser(Optional.ofNullable(snapshot.requestedRealUser()).filter(name -> !name.isBlank())
                            .orElse(dto.getUsername()));
                }
            }
        }
        return dto;
    }

    private DeviceApprovalAction toDeviceApprovalAction(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof DeviceApprovalAction action) {
            return action;
        }
        return DeviceApprovalAction.valueOf(value.toString());
    }

    private ApprovalStatus toApprovalStatus(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ApprovalStatus status) {
            return status;
        }
        return ApprovalStatus.valueOf(value.toString());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private String toStringSafe(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text != null ? text.trim() : null;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceDto> findAllDevicesForAdmin(AdminDeviceSearchRequest request) {
        return findAdminDevicesForLedger(request, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceDto> findDisposedDevicesForAdmin(AdminDeviceSearchRequest request) {
        return findAdminDevicesForLedger(request, true);
    }

    private PageResponse<DeviceDto> findAdminDevicesForLedger(AdminDeviceSearchRequest request, boolean disposedOnly) {
        AdminDeviceSearchRequest safeRequest = request == null
                ? new AdminDeviceSearchRequest(1, 10, "categoryName", null, null, "categoryName", "asc")
                : request;

        AdminSearchContext context = toAdminSearchContext(safeRequest, disposedOnly);

        DeviceSpecifications.AdminDeviceSearchContext specContext =
                new DeviceSpecifications.AdminDeviceSearchContext(
                        context.filterField(),
                        context.filterValue(),
                        context.keyword(),
                        context.disposedOnly());

        Sort sort = buildAdminSort(context.sortField(), context.ascending());
        Pageable pageable = PageRequest.of(Math.max(context.page() - 1, 0), context.size(), sort);

        Page<Devices> devicePage = devicesRepository.findAll(DeviceSpecifications.adminSearch(specContext), pageable);

        List<String> pageIds = devicePage.getContent().stream()
                .map(Devices::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Map<String, List<Map<String, Object>>> historyMap = pageIds.isEmpty()
                ? Collections.emptyMap()
                : buildHistoryMapByIds(new LinkedHashSet<>(pageIds));

        Map<String, Devices> detailedDeviceMap = pageIds.isEmpty()
                ? Collections.emptyMap()
                : devicesRepository.findAllWithDetailsByIdIn(new LinkedHashSet<>(pageIds)).stream()
                        .collect(Collectors.toMap(Devices::getId,
                                Function.identity(),
                                (existing, replacement) -> existing,
                                LinkedHashMap::new));

        List<DeviceDto> content = new ArrayList<>();
        for (Devices shallowDevice : devicePage.getContent()) {
            if (shallowDevice == null || shallowDevice.getId() == null) {
                continue;
            }
            Devices detailed = detailedDeviceMap.getOrDefault(shallowDevice.getId(), shallowDevice);
            List<Map<String, Object>> history = historyMap.getOrDefault(shallowDevice.getId(), Collections.emptyList());
            content.add(toDeviceDto(detailed, history));
        }

        List<DeviceSummary> metadataSource = loadAdminSummaries(disposedOnly);
        Map<String, Object> metadata = buildAdminMetadata(metadataSource, disposedOnly);

        int totalPages = Math.max(devicePage.getTotalPages(), 1);
        return PageResponse.of(content,
                context.page(),
                context.size(),
                devicePage.getTotalElements(),
                totalPages,
                metadata);
    }

    private AdminSearchContext toAdminSearchContext(AdminDeviceSearchRequest request, boolean disposedOnly) {
        int size = clampSize(request.size());
        int safePage = request.page() > 0 ? request.page() : 1;
        String filterField = normalizeAdminFilterField(request.filterField());
        String keyword = normalizeKeyword(request.keyword());
        String filterValue = normalizeFilterValue(request.filterValue());
        String sortField = normalizeAdminSortField(request.sortField());
        boolean ascending = !"desc".equalsIgnoreCase(request.sortDirection());
        return new AdminSearchContext(safePage, size, filterField, keyword, filterValue, sortField, ascending, disposedOnly);
    }

    private Sort buildAdminSort(String sortField, boolean ascending) {
        Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = switch (sortField) {
            case "id" -> "id";
            case "username" -> "realUser";
            case "status" -> "status";
            case "manageDepName" -> "manageDep.name";
            case "projectName" -> "projectId.name";
            case "purpose" -> "purpose";
            case "company" -> "company";
            case "model" -> "model";
            case "description" -> "description";
            case "purchaseDate" -> "purchaseDate";
            case "price" -> "price";
            default -> "categoryId.name";
        };
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private List<DeviceSummary> loadAdminSummaries(boolean disposedOnly) {
        DeviceSpecifications.AdminDeviceSearchContext specContext =
                new DeviceSpecifications.AdminDeviceSearchContext(null, null, null, disposedOnly);
        List<Devices> devices = devicesRepository.findAll(DeviceSpecifications.adminSearch(specContext));
        if (devices.isEmpty()) {
            return List.of();
        }
        return devices.stream()
                .map(DeviceSummary::from)
                .toList();
    }

    private record AdminSearchContext(int page,
                                      int size,
                                      String filterField,
                                      String keyword,
                                      String filterValue,
                                      String sortField,
                                      boolean ascending,
                                      boolean disposedOnly) {
    }

    private Map<String, Object> buildAdminMetadata(List<DeviceSummary> baseList, boolean disposedOnly) {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Map<String, List<String>> filters = new HashMap<>();
        filters.put("categoryName", collectUniqueValues(baseList, DeviceSummary::categoryName, collator));
        filters.put("username", collectUniqueValues(baseList, this::displayUser, collator));
        filters.put("manageDepName", collectUniqueValues(baseList, DeviceSummary::manageDepName, collator));
        filters.put("projectName", collectUniqueValues(baseList, DeviceSummary::projectName, collator));
        filters.put("purpose", collectUniqueValues(baseList, DeviceSummary::purpose, collator));
        filters.put("company", collectUniqueValues(baseList, DeviceSummary::company, collator));
        filters.put("status", collectUniqueValues(baseList, DeviceSummary::status, collator));
        filters.put("id", List.of());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filters", filters);
        Map<String, Long> statusCounts = baseList.stream()
                .collect(Collectors.groupingBy(summary -> Optional.ofNullable(normalizeForFilter(summary.status())).orElse("UNKNOWN"),
                        Collectors.counting()));
        metadata.put("statusCounts", statusCounts);
        metadata.put("totalRecords", baseList.size());
        metadata.put("disposedOnly", disposedOnly);
        metadata.put("pageSizeOptions", List.of(10, 25, 50));
        return metadata;
    }

    private List<String> collectUniqueValues(List<DeviceSummary> source,
                                             Function<DeviceSummary, String> extractor,
                                             Collator collator) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new TreeSet<>(collator);
        for (DeviceSummary summary : source) {
            String value = normalizeForFilter(extractor.apply(summary));
            if (value != null) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> findAvailableDeviceCountsByCategory() {
        List<DeviceCategorySummary> summaries = devicesRepository.findCategorySummaries();
        if (summaries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> deviceIds = summaries.stream()
                .map(DeviceCategorySummary::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Map<String, LatestApprovalSnapshot> snapshotMap = loadLatestApprovalSnapshots(deviceIds);

        Map<String, Long> categoryCounts = new HashMap<>();
        for (DeviceCategorySummary summary : summaries) {
            if (summary == null || summary.getId() == null) {
                continue;
            }
            LatestApprovalSnapshot snapshot = snapshotMap.get(summary.getId());
            if (!isDeviceAvailableBySnapshot(summary.getIsUsable(), snapshot)) {
                continue;
            }
            String categoryName = Optional.ofNullable(summary.getCategoryName())
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .orElse(null);
            if (categoryName == null) {
                continue;
            }
            categoryCounts.merge(categoryName, 1L, Long::sum);
        }
        return categoryCounts;
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

    private String displayUser(DeviceSummary summary) {
        if (summary == null) {
            return null;
        }
        return normalizeForFilter(summary.realUser());
    }

    private record LatestApprovalSnapshot(String deviceId,
                                          DeviceApprovalAction action,
                                          ApprovalStatus status,
                                          Long requestId,
                                          LocalDateTime dueDate,
                                          LocalDateTime createdDate,
                                          String requesterName,
                                          String requestedRealUser) {

        String statusDisplayName() {
            return status != null ? status.getDisplayName() : null;
        }

        String actionDisplayName() {
            return action != null ? action.getDisplayName() : null;
        }

        boolean assignsUser() {
            return action == DeviceApprovalAction.RENTAL && status == ApprovalStatus.APPROVED;
        }
    }

    private record DeviceSummary(String id,
                                 String categoryName,
                                 String manageDepName,
                                 String projectName,
                                 String purpose,
                                 java.util.Date purchaseDate,
                                 String model,
                                 String company,
                                 String sn,
                                 String status,
                                 String description,
                                 Long price,
                                 String realUser) {

        static DeviceSummary from(Devices device) {
            if (device == null) {
                return new DeviceSummary(null, null, null, null, null, null, null, null, null, null, null, null, null);
            }
            return new DeviceSummary(
                    device.getId(),
                    Optional.ofNullable(device.getCategoryId()).map(Categories::getName).orElse(null),
                    Optional.ofNullable(device.getManageDep()).map(Departments::getName).orElse(null),
                    Optional.ofNullable(device.getProjectId()).map(Projects::getName).orElse(null),
                    device.getPurpose(),
                    device.getPurchaseDate(),
                    device.getModel(),
                    device.getCompany(),
                    device.getSn(),
                    device.getStatus(),
                    device.getDescription(),
                    device.getPrice(),
                    device.getRealUser()
            );
        }
    }

    private String sortableString(String value) {
        return normalizeForFilter(value);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceDto> findDevicesForCurrentUser(MyDeviceSearchRequest request) {
        UUID userUuid = commonLookupService.currentUserIdFromJwt()
                .flatMap(this::parseUuidSafely)
                .orElse(null);

        Set<String> usernameKeys = resolveCurrentUserKeys();

        if (userUuid == null && usernameKeys.isEmpty()) {
            return PageResponse.of(Collections.emptyList(), 1, clampSize(request.size()), 0, 1,
                    buildEmptyMyDeviceMetadata());
        }

        LinkedHashSet<Devices> uniqueDevices = new LinkedHashSet<>();
        if (userUuid != null) {
            uniqueDevices.addAll(devicesRepository.findAllForUser(userUuid, null));
        }
        if (!usernameKeys.isEmpty()) {
            uniqueDevices.addAll(devicesRepository.findAllForUsernames(usernameKeys));
        }

        if (uniqueDevices.isEmpty()) {
            return PageResponse.of(Collections.emptyList(), 1, clampSize(request.size()), 0, 1,
                    buildEmptyMyDeviceMetadata());
        }

    List<Devices> baseDevices = uniqueDevices.stream()
        .filter(device -> !isHiddenByDisposalStatus(device))
        .filter(device -> ownsDevice(device, usernameKeys, userUuid))
        .toList();

    if (baseDevices.isEmpty()) {
        return PageResponse.of(Collections.emptyList(), 1, clampSize(request.size()), 0, 1,
            buildEmptyMyDeviceMetadata());
    }

        List<String> deviceIds = baseDevices.stream()
                .map(Devices::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Map<String, LatestApprovalSnapshot> snapshotMap = loadLatestApprovalSnapshots(deviceIds);
        Map<String, List<String>> tagsMap = loadTagsByDeviceIds(deviceIds);

        List<DeviceDto> dtoList = baseDevices.stream()
                .map(device -> toMyDeviceDto(device,
                        snapshotMap.get(device.getId()),
                        tagsMap.getOrDefault(device.getId(), List.of())))
                .filter(this::includeInMyDevices)
                .toList();

        Map<String, Object> metadata = buildMyDeviceMetadata(dtoList);

        String filterField = normalizeMyDeviceFilterField(request.filterField());
        String keyword = normalizeKeyword(request.keyword());
        String chipValue = normalizeChipValue(request.chipValue());
        String sortField = normalizeMyDeviceSortField(request.sortField());
        boolean ascending = !"desc".equalsIgnoreCase(request.sortDirection());

        List<DeviceDto> filtered = dtoList.stream()
                .filter(dto -> matchesMyDeviceChip(dto, filterField, chipValue))
                .filter(dto -> matchesMyDeviceKeyword(dto, filterField, keyword))
                .collect(Collectors.toList());

        Comparator<DeviceDto> comparator = buildMyDeviceComparator(sortField, ascending);
        filtered.sort(comparator);

        int size = clampSize(request.size());
        int totalElements = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / size));
        int page = clampPage(request.page(), totalPages, totalElements);
        int fromIndex = Math.min((page - 1) * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<DeviceDto> pageContent = new ArrayList<>(filtered.subList(fromIndex, toIndex));

        Set<String> pageIds = pageContent.stream()
                .map(DeviceDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<Map<String, Object>>> historyMap = buildHistoryMapByIds(pageIds, 1);
        for (DeviceDto dto : pageContent) {
            dto.setHistory(historyMap.getOrDefault(dto.getId(), Collections.emptyList()));
        }

        return PageResponse.of(pageContent, page, size, totalElements, totalPages, metadata);
    }

    @Transactional
    public DeviceDto updateMyDeviceDescription(String deviceId, String description) {
        UUID jwtUserUuid = commonLookupService.currentUserIdFromJwt()
                .flatMap(this::parseUuidSafely)
                .orElse(null);
        String normalizedUsername = commonLookupService.currentUsernameFromJwt()
                .map(this::normalizeKeyword)
                .orElse(null);

        Set<String> usernameKeys = resolveCurrentUserKeys();

        UUID effectiveUuid = jwtUserUuid;
        if (effectiveUuid == null && normalizedUsername != null) {
            effectiveUuid = commonLookupService.resolveKeycloakUserInfoByUsername(normalizedUsername)
                    .map(CommonLookupService.KeycloakUserInfo::id)
                    .orElse(null);
        }

        if (effectiveUuid == null && usernameKeys.isEmpty()) {
            throw new CustomException(CustomErrorCode.DEVICE_USER_NOT_IDENTIFIED);
        }

        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
                        "Device not found: " + deviceId));

        if (!ownsDevice(device, usernameKeys, effectiveUuid)) {
            throw new CustomException(CustomErrorCode.DEVICE_NOT_OWNED_BY_USER);
        }

        String normalizedDescription = Optional.ofNullable(description)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);

        device.setDescription(normalizedDescription);
        devicesRepository.save(device);

    return toDeviceDto(device);
    }

    private boolean ownsDevice(Devices device, Set<String> normalizedUsernameKeys, UUID expectedUserUuid) {
        if (device == null || (normalizedUsernameKeys == null && expectedUserUuid == null)) {
            return false;
        }

        if (expectedUserUuid != null) {
            UUID deviceUuid = device.getUserUuid();
            if (deviceUuid != null && expectedUserUuid.equals(deviceUuid)) {
                return true;
            }
        }

        if (normalizedUsernameKeys == null || normalizedUsernameKeys.isEmpty()) {
            return false;
        }

        String realUserKey = normalizeUsernameKey(device.getRealUser());
        if (realUserKey != null) {
            if (normalizedUsernameKeys.contains(realUserKey)) {
                return true;
            }
            String withoutSpaces = realUserKey.replace(" ", "");
            if (!withoutSpaces.isBlank() && normalizedUsernameKeys.contains(withoutSpaces)) {
                return true;
            }
            int atIndex = realUserKey.indexOf('@');
            if (atIndex > 0) {
                String localPart = realUserKey.substring(0, atIndex);
                if (!localPart.isBlank() && normalizedUsernameKeys.contains(localPart)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isHiddenByDisposalStatus(Devices device) {
        if (device == null) {
            return true;
        }
        String status = device.getStatus();
        if (status == null || !Constants.DISPOSE_TYPE.equals(status)) {
            return false;
        }
        return !hasActiveDisposalApproval(device);
    }

    private boolean hasActiveDisposalApproval(Devices device) {
        if (device == null || device.getApprovalDetails() == null) {
            return false;
        }
        return device.getApprovalDetails().stream()
                .filter(detail -> detail != null && detail.getAction() == DeviceApprovalAction.DISPOSAL)
                .map(DeviceApprovalDetail::getRequest)
                .filter(Objects::nonNull)
                .map(ApprovalRequest::getStatus)
                .anyMatch(status -> status == ApprovalStatus.PENDING || status == ApprovalStatus.IN_PROGRESS);
    }

    @SuppressWarnings("unused")
    private boolean isDisposalRequestApproved(Devices device) {
        return findLatestDetail(device)
                .filter(detail -> detail.getAction() == DeviceApprovalAction.DISPOSAL)
                .map(DeviceApprovalDetail::getRequest)
                .filter(Objects::nonNull)
                .map(ApprovalRequest::getStatus)
                .filter(status -> status == ApprovalStatus.APPROVED)
                .isPresent();
    }

    private Set<String> resolveCurrentUserKeys() {
        Set<String> keys = new LinkedHashSet<>();
        commonLookupService.currentUsernameFromJwt().ifPresent(value -> addUsernameCandidate(keys, value));
        commonLookupService.currentUserEmailFromJwt().ifPresent(value -> addUsernameCandidate(keys, value));
        commonLookupService.currentUserDisplayNameFromJwt().ifPresent(value -> addUsernameCandidate(keys, value));
        return keys;
    }

    private void addUsernameCandidate(Set<String> collector, String rawCandidate) {
        String normalized = normalizeUsernameKey(rawCandidate);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        collector.add(normalized);
        int atIndex = normalized.indexOf('@');
        if (atIndex > 0) {
            String localPart = normalized.substring(0, atIndex);
            if (!localPart.isBlank()) {
                collector.add(localPart);
            }
        }
        if (normalized.indexOf(' ') >= 0) {
            String withoutSpaces = normalized.replace(" ", "");
            if (!withoutSpaces.isBlank()) {
                collector.add(withoutSpaces);
            }
        }
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

    private boolean includeInMyDevices(DeviceDto dto) {
        if (dto == null) {
            return false;
        }

        String approvalType = Optional.ofNullable(dto.getApprovalType())
                .map(String::trim)
                .orElse(null);
        if (!Constants.APPROVAL_RENTAL.equals(approvalType)) {
            return true;
        }

        String approvalInfo = Optional.ofNullable(dto.getApprovalInfo())
                .map(String::trim)
                .orElse(null);
        return approvalInfo == null
                || approvalInfo.isEmpty()
                || Constants.APPROVAL_COMPLETED.equals(approvalInfo);
    }


    public DeviceDto findById(String id) {
        Devices device = devicesRepository.findById(id)
        .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
            "Device not found: " + id));
    return toDeviceDto(device);
    }

    @Transactional
    public DeviceDto disposeDeviceByAdmin(String deviceId, String reason, String operatorOverride) {
        Devices device = devicesRepository.findById(deviceId)
        .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
            "Device not found: " + deviceId));

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
        .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
            "Device not found: " + deviceId));

        if (!Constants.DISPOSE_TYPE.equals(device.getStatus())) {
        throw new CustomException(CustomErrorCode.DEVICE_RECOVERY_NOT_DISPOSED);
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
    public DeviceDto forceReturnDeviceByAdmin(String deviceId, String reason, String operatorOverride) {
    Devices device = devicesRepository.findById(deviceId)
    .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
        "Device not found: " + deviceId));

    if (Boolean.TRUE.equals(device.getIsUsable())) {
        return new DeviceDto(device, buildHistory(deviceId));
    }

    String operatorUsername = resolveOperatorUsername(operatorOverride);
    validateOperatorUser(operatorUsername);

    device.setIsUsable(Boolean.TRUE);
    device.setUserUuid(null);
    device.setRealUser(null);
    devicesRepository.save(device);

    ApprovalRequest returnRequest = ApprovalRequest.builder()
        .category(ApprovalCategory.DEVICE)
        .status(ApprovalStatus.APPROVED)
        .title(buildSystemTitle(DeviceApprovalAction.RETURN, device))
        .reason(buildOperatorTaggedReason(
            Optional.ofNullable(reason).map(String::trim).filter(r -> !r.isEmpty())
                .orElse("관리자 강제 반납 처리"),
            operatorUsername))
        .build();
    returnRequest.setSubmittedAt(LocalDateTime.now());
    returnRequest.setCompletedAt(LocalDateTime.now());

    DeviceApprovalDetail detail = DeviceApprovalDetail.create();
    detail.setDevice(device);
    detail.setAction(DeviceApprovalAction.RETURN);
    detail.setRequestedProject(device.getProjectId());
    detail.setRequestedRealUser(operatorUsername);
    detail.attachTo(returnRequest);
    device.getApprovalDetails().add(detail);

    approvalRequestRepository.save(returnRequest);

    return new DeviceDto(device, buildHistory(deviceId));
    }

    @Transactional
    public DeviceDto updateDevice(String id, DeviceDto dto) {
        Devices device = devicesRepository.findById(id)
        .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
            "Device not found: " + id));

    String previousApplicant = normalizeBlank(device.getRealUser());
    boolean wasUsable = Boolean.TRUE.equals(device.getIsUsable());
    String newApplicant = normalizeBlank(dto.getUsername());
    String newRealUser = normalizeBlank(dto.getRealUser());

    CommonLookupService.KeycloakUserInfo newApplicantInfo = null;
    if (newApplicant != null) {
        newApplicantInfo = commonLookupService.resolveKeycloakUserInfoByUsername(newApplicant)
            .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_APPLICANT_NOT_FOUND,
                "Keycloak에서 신청자를 찾을 수 없습니다: " + newApplicant));
    }

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
                dto.getVatIncluded(),
                dto.getMacAddress(),
                dto.getPurchaseDate()
        );

        String operatorUsername = resolveOperatorUsername(null);
        validateOperatorUser(operatorUsername);

        boolean applicantChanged = !equalsIgnoreCase(previousApplicant, newApplicant);

        if (applicantChanged) {
            rejectActiveRentalRequests(device, operatorUsername, newApplicant, newRealUser);

            if (previousApplicant != null) {
                recordImmediateReturn(device, previousApplicant, operatorUsername);
            } else if (!wasUsable) {
                device.setIsUsable(Boolean.TRUE);
                device.setRealUser(null);
                device.setUserUuid(null);
            }

            if (newApplicant != null) {
                recordImmediateRental(device, newApplicant, newRealUser, newApplicantInfo, operatorUsername);
            } else {
                device.setRealUser(null);
                device.setUserUuid(null);
                device.setIsUsable(Boolean.TRUE);
            }
        } else {
            applyHolderAssignment(device, newApplicantInfo, newApplicant, newRealUser);

            if (newApplicant != null) {
                boolean wasUsableBeforeAssignment = Boolean.TRUE.equals(device.getIsUsable());
                device.setIsUsable(Boolean.FALSE);
                boolean finalized = finalizePendingRentalIfPresent(device, newApplicant, newRealUser, newApplicantInfo, operatorUsername);
                if (!finalized && wasUsableBeforeAssignment) {
                    recordImmediateRental(device, newApplicant, newRealUser, newApplicantInfo, operatorUsername);
                }
            } else {
                device.setRealUser(null);
                device.setUserUuid(null);
                device.setIsUsable(Boolean.TRUE);
            }

            if (dto.getIsUsable() != null) {
                device.setIsUsable(dto.getIsUsable());
            }
        }

        devicesRepository.save(device);
        return new DeviceDto(device, buildHistory(id));
    }

    private void rejectActiveRentalRequests(Devices device,
                                            String operatorUsername,
                                            String takeoverApplicant,
                                            String takeoverRealUser) {
        if (device == null) {
            return;
        }

        for (DeviceApprovalDetail detail : device.getApprovalDetails()) {
            if (detail.getAction() != DeviceApprovalAction.RENTAL) {
                continue;
            }
            ApprovalRequest request = detail.getRequest();
            if (request == null) {
                continue;
            }

            ApprovalStatus status = request.getStatus();
            if (status != ApprovalStatus.PENDING && status != ApprovalStatus.IN_PROGRESS) {
                continue;
            }

            String applicantName = Optional.ofNullable(request.getRequesterName())
                    .filter(name -> !name.isBlank())
                    .orElseGet(() -> Optional.ofNullable(detail.getRequestedRealUser())
                            .filter(name -> !name.isBlank())
                            .orElseGet(() -> Optional.ofNullable(takeoverRealUser)
                                    .filter(name -> !name.isBlank())
                                    .orElse(takeoverApplicant)));

            String baseReason = applicantName != null
                    ? String.format("관리자 편집으로 '%s' 대여 신청 반려 처리", applicantName)
                    : "관리자 편집으로 진행 중이던 대여 신청 반려 처리";

            request.setReason(buildOperatorTaggedReason(baseReason, operatorUsername));
            request.markRejected();

            request.getSteps().forEach(step -> {
                if (step.getStatus() != StepStatus.REJECTED) {
                    step.markRejected("관리자 편집으로 자동 반려 처리");
                }
            });

            approvalRequestRepository.save(request);
        }
    }

    private Optional<DeviceApprovalDetail> findLatestPendingRentalDetail(Devices device) {
        if (device == null) {
            return Optional.empty();
        }

        return device.getApprovalDetails().stream()
                .filter(detail -> detail.getAction() == DeviceApprovalAction.RENTAL)
                .filter(detail -> {
                    ApprovalRequest request = detail.getRequest();
                    if (request == null) {
                        return false;
                    }
                    ApprovalStatus status = request.getStatus();
                    return status == ApprovalStatus.PENDING || status == ApprovalStatus.IN_PROGRESS;
                })
                .max(Comparator.comparing(
                        detail -> Optional.ofNullable(detail.getRequest())
                                .map(ApprovalRequest::getCreatedDate)
                                .orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private boolean finalizePendingRentalIfPresent(Devices device,
                                                   String applicantUsername,
                                                   String realUserName,
                                                   CommonLookupService.KeycloakUserInfo applicantInfo,
                                                   String operatorUsername) {
        if (device == null || applicantUsername == null || applicantUsername.isBlank()) {
            return false;
        }

        Optional<DeviceApprovalDetail> pendingDetailOpt = findLatestPendingRentalDetail(device);
        if (pendingDetailOpt.isEmpty()) {
            return false;
        }

        DeviceApprovalDetail detail = pendingDetailOpt.get();
        ApprovalRequest request = detail.getRequest();
        if (request == null) {
            return false;
        }

        String baseReason = String.format("관리자 편집으로 '%s' 대여 승인 완료", applicantUsername);
        String taggedReason = buildOperatorTaggedReason(baseReason, operatorUsername);
        String existingReason = Optional.ofNullable(request.getReason()).orElse("");
        if (existingReason.isBlank()) {
            request.setReason(taggedReason);
        } else if (!existingReason.contains(baseReason)) {
            request.setReason(existingReason + System.lineSeparator() + taggedReason);
        }

        device.setIsUsable(Boolean.FALSE);
        applyHolderAssignment(device, applicantInfo, applicantUsername, realUserName);
        applyRequesterMetadata(request, applicantUsername, device.getUserUuid());

        request.getSteps().forEach(step -> {
            if (!step.isApproved()) {
                step.markApproved("관리자 편집으로 자동 승인 처리");
            }
        });
        request.markApproved();

        detail.updateFromDevice(device);
        approvalRequestRepository.save(request);
        return true;
    }

    private void recordImmediateReturn(Devices device, String previousApplicant, String operatorUsername) {
        if (previousApplicant == null || previousApplicant.isBlank()) {
            return;
        }

        ApprovalRequest returnRequest = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.APPROVED)
                .title(buildSystemTitle(DeviceApprovalAction.RETURN, device))
                .reason(buildOperatorTaggedReason(
                        String.format("관리자 편집으로 기존 신청자 '%s' 강제 반납 처리", previousApplicant),
                        operatorUsername))
                .build();
        returnRequest.setSubmittedAt(LocalDateTime.now());
        returnRequest.setCompletedAt(LocalDateTime.now());
    applyRequesterMetadata(returnRequest, previousApplicant, device.getUserUuid());

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(DeviceApprovalAction.RETURN);
        detail.updateFromDevice(device);
        detail.attachTo(returnRequest);
        device.getApprovalDetails().add(detail);

        approvalRequestRepository.save(returnRequest);

        device.setIsUsable(Boolean.TRUE);
        device.setRealUser(null);
        device.setUserUuid(null);
    }

    private void recordImmediateRental(Devices device,
                                       String applicantUsername,
                                       String realUserName,
                                       CommonLookupService.KeycloakUserInfo applicantInfo,
                                       String operatorUsername) {
        if (applicantUsername == null || applicantUsername.isBlank()) {
            return;
        }
        if (applicantInfo == null || applicantInfo.id() == null) {
            throw new CustomException(CustomErrorCode.DEVICE_APPLICANT_NOT_FOUND,
                    "Keycloak에서 신청자를 찾을 수 없습니다: " + applicantUsername);
        }

        device.setIsUsable(Boolean.FALSE);
        applyHolderAssignment(device, applicantInfo, applicantUsername, realUserName);

        ApprovalRequest rentalRequest = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.APPROVED)
                .title(buildSystemTitle(DeviceApprovalAction.RENTAL, device))
                .reason(buildOperatorTaggedReason(
                        String.format("관리자 편집으로 '%s' 강제 대여 처리", applicantUsername),
                        operatorUsername))
                .build();
        rentalRequest.setSubmittedAt(LocalDateTime.now());
        rentalRequest.setCompletedAt(LocalDateTime.now());
    applyRequesterMetadata(rentalRequest, applicantUsername, device.getUserUuid());

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(DeviceApprovalAction.RENTAL);
        detail.updateFromDevice(device);
        detail.attachTo(rentalRequest);
        device.getApprovalDetails().add(detail);

        approvalRequestRepository.save(rentalRequest);
    }

    private void enrichDeviceUserProfile(Devices device, DeviceDto dto) {
        if (device == null || dto == null) {
            return;
        }

        UUID userUuid = device.getUserUuid();
        if (userUuid != null) {
            commonLookupService.resolveKeycloakUserInfoById(userUuid).ifPresent(userInfo -> {
                if (userInfo.username() != null && !userInfo.username().isBlank()) {
                    dto.setUsername(userInfo.username());
                }
                if (userInfo.email() != null && !userInfo.email().isBlank()) {
                    dto.setUserEmail(userInfo.email());
                }
                if ((dto.getRealUser() == null || dto.getRealUser().isBlank())
                        && userInfo.displayName() != null && !userInfo.displayName().isBlank()) {
                    dto.setRealUser(userInfo.displayName());
                }
            });
        }
        if ((dto.getUsername() == null || dto.getUsername().isBlank()) && dto.getRealUser() != null) {
            commonLookupService.resolveKeycloakUserInfoByUsername(dto.getRealUser()).ifPresent(userInfo -> {
                if (userInfo.username() != null && !userInfo.username().isBlank()) {
                    dto.setUsername(userInfo.username());
                }
                if (userInfo.email() != null && !userInfo.email().isBlank()) {
                    dto.setUserEmail(userInfo.email());
                }
            });
        }
    }

    private void applyHolderAssignment(Devices device,
                                       CommonLookupService.KeycloakUserInfo applicantInfo,
                                       String applicantUsername,
                                       String realUserName) {
        if (applicantUsername == null) {
            device.setUserUuid(null);
            device.setRealUser(null);
            return;
        }

        if (applicantInfo == null || applicantInfo.id() == null) {
            throw new CustomException(CustomErrorCode.DEVICE_APPLICANT_NOT_FOUND,
                    "Keycloak에서 신청자를 찾을 수 없습니다: " + applicantUsername);
        }

        device.setUserUuid(applicantInfo.id());
        String effectiveRealUser = realUserName != null && !realUserName.isBlank()
                ? realUserName
                : safeDisplayName(applicantInfo);
        if (effectiveRealUser == null || effectiveRealUser.isBlank()) {
            effectiveRealUser = applicantUsername;
        }
        device.setRealUser(effectiveRealUser);
    }

    private void applyRequesterMetadata(ApprovalRequest request, String username, UUID fallbackUserUuid) {
        if (request == null) {
            return;
        }

        String normalizedUsername = username != null ? username.trim() : null;

        CommonLookupService.KeycloakUserInfo userInfo = normalizedUsername != null && !normalizedUsername.isEmpty()
                ? commonLookupService.resolveKeycloakUserInfoByUsername(normalizedUsername)
                .orElseGet(() -> commonLookupService.fallbackUserInfo(normalizedUsername))
                : null;

        if ((userInfo == null || userInfo.id() == null) && fallbackUserUuid != null) {
            userInfo = commonLookupService.resolveKeycloakUserInfoById(fallbackUserUuid)
                    .orElse(userInfo);
        }

        UUID requesterUuid = null;
        if (userInfo != null && userInfo.id() != null) {
            requesterUuid = userInfo.id();
        } else if (fallbackUserUuid != null) {
            requesterUuid = fallbackUserUuid;
        }
        if (requesterUuid != null) {
            request.setRequesterExternalId(requesterUuid);
        }

        if (userInfo != null) {
            if (userInfo.email() != null && !userInfo.email().isBlank()) {
                request.setRequesterEmail(userInfo.email());
            }
            String displayName = userInfo.displayName();
            if (displayName == null || displayName.isBlank()) {
                displayName = userInfo.username();
            }
            if (displayName != null && !displayName.isBlank()) {
                request.setRequesterName(displayName);
            }
        }

        if ((request.getRequesterName() == null || request.getRequesterName().isBlank())
                && normalizedUsername != null && !normalizedUsername.isBlank()) {
            request.setRequesterName(normalizedUsername);
        }
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
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
                (status == ApprovalStatus.PENDING
                        || status == ApprovalStatus.IN_PROGRESS
                        || status == ApprovalStatus.APPROVED);

        boolean rentalPending = action == DeviceApprovalAction.RENTAL &&
                (status == ApprovalStatus.PENDING || status == ApprovalStatus.IN_PROGRESS);
        if (rentalPending) {
            return false;
        }

        boolean rentalRejected = action == DeviceApprovalAction.RENTAL && status == ApprovalStatus.REJECTED;

        return returnWaiting || rentalRejected || Boolean.TRUE.equals(device.getIsUsable());
    }

    private boolean isDeviceAvailableBySnapshot(Boolean isUsable, LatestApprovalSnapshot snapshot) {
        if (isUsable == null && snapshot == null) {
            return false;
        }
        if (snapshot == null) {
            return Boolean.TRUE.equals(isUsable);
        }

        DeviceApprovalAction action = snapshot.action();
        ApprovalStatus status = snapshot.status();

        boolean returnWaiting = action == DeviceApprovalAction.RETURN &&
                (status == ApprovalStatus.PENDING
                        || status == ApprovalStatus.IN_PROGRESS
                        || status == ApprovalStatus.APPROVED);

        boolean rentalPending = action == DeviceApprovalAction.RENTAL &&
                (status == ApprovalStatus.PENDING || status == ApprovalStatus.IN_PROGRESS);
        if (rentalPending) {
            return false;
        }

        boolean rentalRejected = action == DeviceApprovalAction.RENTAL && status == ApprovalStatus.REJECTED;

        return returnWaiting || rentalRejected || Boolean.TRUE.equals(isUsable);
    }

    private Optional<DeviceApprovalDetail> findLatestDetail(Devices device) {
    if (device == null || device.getApprovalDetails() == null || device.getApprovalDetails().isEmpty()) {
        return Optional.empty();
    }
    return device.getApprovalDetails().stream()
                .max(Comparator.comparing(
                        detail -> Optional.ofNullable(detail.getRequest())
                                .map(ApprovalRequest::getCreatedDate)
                                .orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private List<Map<String, Object>> buildHistory(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return Collections.emptyList();
        }
        return buildHistoryMapByIds(Collections.singleton(deviceId))
                .getOrDefault(deviceId, Collections.emptyList());
    }

    private Map<String, List<Map<String, Object>>> buildHistoryMap(Collection<Devices> devices) {
        if (devices == null || devices.isEmpty()) {
            return Collections.emptyMap();
        }

        LinkedHashSet<String> deviceIds = devices.stream()
                .map(Devices::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return buildHistoryMapByIds(deviceIds);
    }

    private Map<String, List<Map<String, Object>>> buildHistoryMapByIds(Collection<String> deviceIds) {
        return buildHistoryMapByIds(deviceIds, Integer.MAX_VALUE);
    }

    private Map<String, List<Map<String, Object>>> buildHistoryMapByIds(Collection<String> deviceIds, int historyLimit) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        int effectiveLimit = historyLimit <= 0 ? Integer.MAX_VALUE : historyLimit;

        List<DeviceApprovalDetail> histories = deviceApprovalDetailRepository.findHistoryByDeviceIds(deviceIds);

        Map<String, List<DeviceApprovalDetail>> grouped = new LinkedHashMap<>();
        for (DeviceApprovalDetail detail : histories) {
            if (detail == null || detail.getDevice() == null || detail.getDevice().getId() == null) {
                continue;
            }
            String deviceId = detail.getDevice().getId();
            List<DeviceApprovalDetail> bucket = grouped.computeIfAbsent(deviceId, id -> new ArrayList<>());
            if (bucket.size() >= effectiveLimit) {
                continue;
            }
            bucket.add(detail);
        }

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String deviceId : deviceIds) {
            List<DeviceApprovalDetail> deviceDetails = grouped.get(deviceId);
            result.put(deviceId, toHistoryDtoList(deviceDetails));
        }
        return result;
    }

    private List<Map<String, Object>> toHistoryDtoList(List<DeviceApprovalDetail> histories) {
        if (histories == null || histories.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> historyList = new ArrayList<>();
        for (DeviceApprovalDetail detail : histories) {
            ApprovalRequest request = detail.getRequest();
            if (request == null || request.getStatus() != ApprovalStatus.APPROVED) {
                continue;
            }

            Map<String, Object> map = new HashMap<>();

            Optional<String> requesterName = resolveRequesterDisplayName(request);

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

    private String safeDisplayName(CommonLookupService.KeycloakUserInfo userInfo) {
        if (userInfo == null) {
            return null;
        }
        if (userInfo.displayName() != null && !userInfo.displayName().isBlank()) {
            return userInfo.displayName();
        }
        return userInfo.username();
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
                .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_USER_NOT_IDENTIFIED));
    }

    private void validateOperatorUser(String operatorUsername) {
        boolean operatorIsAdmin = commonLookupService.isAdminUser(operatorUsername);
        if (!operatorIsAdmin) {
            commonLookupService.resolveKeycloakUserIdByUsername(operatorUsername)
                    .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_OPERATOR_NOT_FOUND,
                            "Keycloak user not found: " + operatorUsername));
        }
    }

    private Optional<String> resolveRequesterDisplayName(ApprovalRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String requesterName = request.getRequesterName();
        if (requesterName != null && !requesterName.isBlank()) {
            return Optional.of(requesterName);
        }
        UUID externalId = request.getRequesterExternalId();
        if (externalId != null) {
            return commonLookupService.resolveKeycloakUserInfoById(externalId)
                    .map(this::safeDisplayName)
                    .filter(name -> name != null && !name.isBlank());
        }
        String requesterEmail = request.getRequesterEmail();
        if (requesterEmail != null && !requesterEmail.isBlank()) {
            return Optional.of(requesterEmail);
        }
        return Optional.empty();
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
