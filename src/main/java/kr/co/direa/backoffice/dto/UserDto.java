package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
public class UserDto implements Serializable {
    private Long id;
    private String username;
    private String email;
    private String auth;
    private Departments departmentId;

    public UserDto(Users entity) {
        this.id = entity.getId();
        this.username = entity.getUsername();
        this.auth = entity.getAuth();
        this.departmentId = entity.getDepartmentId();
    }

    public Users toEntity() {
        return Users.builder()
                .username(username)
                .email(email)
                .auth(auth)
                .departmentId(departmentId)
                .build();
    }
}
