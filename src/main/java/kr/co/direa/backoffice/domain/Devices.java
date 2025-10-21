package kr.co.direa.backoffice.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "devices")
public class Devices extends BaseTimeEntity {
    @Id
    @Column(name = "id")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users userId;

    @Column(name = "real_user")
    private String realUser;

    @OneToMany(mappedBy = "deviceId", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ApprovalDevices> approvalDevices = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manage_dep")
    private Departments manageDep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Categories categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Projects projectId;

    @Column(name = "spec")
    private String spec;

    @Column(name = "price")
    private Long price;

    @Column(name = "model")
    private String model;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "admin_description", length = 1000)
    private String adminDescription;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeviceTag> deviceTags = new ArrayList<>();

    @Column(name = "company")
    private String company;

    @Column(name = "sn")
    private String sn;

    @Column(name = "status")
    private String status;

    @Column(name = "is_usable")
    private Boolean isUsable;

    @Column(name = "purpose")
    private String purpose;

    @Column(name = "purchase_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date purchaseDate;

    @Builder
    public Devices(String id, Users userId, Departments manageDep, Categories categoryId, String spec,
                   Long price, String model, String description, String company,
                   Projects projectId, String sn, String status, Boolean isUsable, String purpose, Date purchaseDate,
                   List<ApprovalDevices> approvalDevices, String adminDescription) {
        this.id = id;
        this.userId = userId;
        this.manageDep = manageDep;
        this.categoryId = categoryId;
        this.projectId = projectId;
        this.spec = spec;
        this.price = price;
        this.model = model;
        this.description = description;
        this.adminDescription = adminDescription;
        this.company = company;
        this.sn = sn;
        this.status = status;
        this.isUsable = isUsable;
        this.purpose = purpose;
        this.purchaseDate = purchaseDate;
        this.approvalDevices = approvalDevices;
    }

    public void update(Categories category, Projects project, Departments manageDep, long price, String status,
                       String purpose, String description, String adminDescription, String model, String company,
                       String sn, String spec, Date purchaseDate) {
        this.categoryId = category;
        this.projectId = project;
        this.manageDep = manageDep;
        this.price = price;
        this.status = status;
        this.purpose = purpose;
        this.description = description;
        this.adminDescription = adminDescription;
        this.model = model;
        this.company = company;
        this.sn = sn;
        this.spec = spec;
        this.purchaseDate = purchaseDate;
    }

    public void update(Users user, String status, Boolean isUsable, Projects project, Departments manageDep,
                       String description, String adminDescription) {
        this.userId = user;
        this.status = status;
        this.isUsable = isUsable;
        this.projectId = project;
        this.manageDep = manageDep;
        this.description = description;
        this.adminDescription = adminDescription;
    }
}
