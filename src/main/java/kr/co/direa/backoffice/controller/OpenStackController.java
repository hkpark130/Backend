package kr.co.direa.backoffice.controller;

import jakarta.validation.Valid;
import kr.co.direa.backoffice.dto.OpenStackInstanceDto;
import kr.co.direa.backoffice.dto.OpenStackInstanceLookupRequest;
import kr.co.direa.backoffice.service.OpenStackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/openstack")
public class OpenStackController {

    private final OpenStackService openStackService;

    @PostMapping("/instance-by-floating-ip")
    public ResponseEntity<OpenStackInstanceDto> getInstanceByFloatingIp(
            @RequestBody @Valid OpenStackInstanceLookupRequest request) {
        return ResponseEntity.ok(openStackService.getInstanceByFloatingIp(request.floatingIp()));
    }
}
