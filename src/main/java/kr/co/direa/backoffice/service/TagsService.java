package kr.co.direa.backoffice.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.direa.backoffice.domain.DeviceTag;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Tags;
import kr.co.direa.backoffice.exception.CustomException;
import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.repository.TagsRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagsService {
    private final DevicesRepository devicesRepository;
    private final TagsRepository tagsRepository;

    @Transactional
    public void replaceDeviceTags(String deviceId, List<String> tagNames) {
        Devices device = devicesRepository.findById(deviceId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
                        "Device not found: " + deviceId));
        replaceDeviceTags(device, tagNames);
    }

    @Transactional
    public void replaceDeviceTags(Devices device, List<String> tagNames) {
        if (device == null) {
            throw new CustomException(CustomErrorCode.DEVICE_NOT_FOUND, "Device not supplied");
        }

        List<String> sanitized = sanitizeTags(tagNames);
        device.getDeviceTags().clear();

        if (sanitized.isEmpty()) {
            devicesRepository.save(device);
            return;
        }

        for (String tagName : sanitized) {
            Tags tag = tagsRepository.findByNameIgnoreCase(tagName)
                    .orElseGet(() -> tagsRepository.save(Tags.builder().name(tagName).build()));
            DeviceTag deviceTag = DeviceTag.builder()
                    .device(device)
                    .tag(tag)
                    .build();
            device.getDeviceTags().add(deviceTag);
        }

        devicesRepository.save(device);
    }

    @Transactional(readOnly = true)
    public List<String> findAllTagNames() {
        List<Tags> entities = tagsRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Tags tag : entities) {
            String display = normalizeTag(tag != null ? tag.getName() : null);
            if (display.isEmpty()) {
                continue;
            }
            String key = display.toLowerCase(Locale.ROOT);
            normalized.putIfAbsent(key, display);
        }
        return new ArrayList<>(normalized.values());
    }

    private List<String> sanitizeTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String candidate : tagNames) {
            String normalized = normalizeTag(candidate);
            if (normalized.isEmpty()) {
                continue;
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, normalized);
        }
        return new ArrayList<>(unique.values());
    }

    private String normalizeTag(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed;
    }
}
