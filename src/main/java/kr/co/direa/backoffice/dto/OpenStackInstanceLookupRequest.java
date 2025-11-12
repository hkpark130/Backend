package kr.co.direa.backoffice.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenStackInstanceLookupRequest(@NotBlank String floatingIp) {
}
