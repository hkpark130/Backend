package kr.co.direa.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Request payload carrying device application data from the front-end.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceApplicationRequestDto {
    private Long approvalId;
    private String userName;
    private String realUser;
    private String reason;
    private String deviceId;
    private String deviceStatus;
    private String devicePurpose;
    private String description;
    private String categoryName;
    private String projectName;
    private String departmentName;
    private String img;
    private String type;
    private String status;
    private Boolean isUsable;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime deadline;
    private List<String> tag = new ArrayList<>();
    private List<String> approvers = new ArrayList<>();
}
