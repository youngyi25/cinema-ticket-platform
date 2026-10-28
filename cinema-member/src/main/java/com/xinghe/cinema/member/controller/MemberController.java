package com.xinghe.cinema.member.controller;

import com.xinghe.cinema.common.result.Result;
import com.xinghe.cinema.member.entity.Coupon;
import com.xinghe.cinema.member.entity.Member;
import com.xinghe.cinema.member.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * member-service 对外接口。
 */
@RestController
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/{id}")
    public Result<Member> detail(@PathVariable Long id) {
        return Result.ok(memberService.getRequired(id));
    }

    @PostMapping
    public Result<Member> create(@RequestBody Member member) {
        return Result.ok(memberService.create(member));
    }

    @GetMapping("/{id}/coupons")
    public Result<List<Coupon>> coupons(@PathVariable Long id) {
        return Result.ok(memberService.listCoupons(id));
    }

    @PostMapping("/coupon")
    public Result<Coupon> issueCoupon(@RequestBody Coupon coupon) {
        return Result.ok(memberService.issueCoupon(coupon));
    }
}
