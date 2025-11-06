package kr.co.direa.backoffice.controller;

import jakarta.validation.Valid;
import kr.co.direa.backoffice.dto.ldap.LdapUserCreateRequest;
import kr.co.direa.backoffice.dto.ldap.LdapUserResponse;
import kr.co.direa.backoffice.service.LdapUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LdapController {

    private final LdapUserService ldapUserService;

    @GetMapping("/ldap-users")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<List<LdapUserResponse>> getUsers() {
        return ResponseEntity.ok(ldapUserService.fetchUsers());
    }

    @GetMapping("/check-ldap-user-cn/{cn}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<Boolean> checkCn(@PathVariable String cn) {
        return ResponseEntity.ok(ldapUserService.isCnAvailable(cn));
    }

    @GetMapping("/check-ldap-user-uidnum/{uidNumber}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<Boolean> checkUidNumber(@PathVariable Integer uidNumber) {
        return ResponseEntity.ok(ldapUserService.isUidNumberAvailable(uidNumber));
    }

    @PostMapping("/ldap-user")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<Map<String, String>> createUser(@Valid @RequestBody LdapUserCreateRequest request) {
        ldapUserService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "created", "cn", request.getCn()));
    }

    @DeleteMapping("/ldap-user/{cn}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String cn) {
        ldapUserService.deleteUser(cn);
        return ResponseEntity.ok(Map.of("message", "deleted", "cn", cn));
    }

    @GetMapping("/reissue-password/{cn}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<String> reissuePassword(@PathVariable String cn) {
        String password = ldapUserService.reissuePassword(cn);
        return ResponseEntity.ok(password);
    }
}
