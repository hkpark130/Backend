package kr.co.direa.backoffice.vo;

import lombok.Data;

import java.util.List;

@Data
public class ApproverUpdateRequest {
    private List<String> approverUsernames;
}
