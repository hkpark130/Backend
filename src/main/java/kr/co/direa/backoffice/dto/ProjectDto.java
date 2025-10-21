package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.Projects;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
public class ProjectDto implements Serializable {
    private Long id;
    private String name;
    private String code;

    public ProjectDto(Projects entity) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.code = entity.getCode();
    }
}
