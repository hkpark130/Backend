package kr.co.direa.backoffice.domain;

import jakarta.persistence.*;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class Users extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "username")
    private String username;

    @Column(name = "auth")
    private String auth;

    @Column(name = "external_id", length = 36)
    private UUID externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Departments departmentId;

    @Builder
    public Users(Long id, String username, String email, String auth, Departments departmentId, UUID externalId) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.auth = auth;
        this.departmentId = departmentId;
        this.externalId = externalId;
    }
}
