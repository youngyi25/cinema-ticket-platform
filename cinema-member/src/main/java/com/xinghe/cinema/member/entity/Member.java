package com.xinghe.cinema.member.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member")
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String nickname;

    /** 1 正常 / 0 禁用。 */
    private Integer status;

    private LocalDateTime createdAt;
}
