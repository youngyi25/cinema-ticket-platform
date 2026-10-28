package com.xinghe.cinema.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量排片请求（文档 5.4）：整批要么全成功、要么全失败。
 */
@Data
public class BatchScheduleRequest {

    @NotEmpty(message = "场次列表不能为空")
    @Valid
    private List<CreateSessionRequest> sessions;
}
