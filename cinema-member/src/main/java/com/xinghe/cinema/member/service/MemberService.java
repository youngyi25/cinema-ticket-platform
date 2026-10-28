package com.xinghe.cinema.member.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinghe.cinema.common.exception.BizException;
import com.xinghe.cinema.member.entity.Coupon;
import com.xinghe.cinema.member.entity.Member;
import com.xinghe.cinema.member.mapper.CouponMapper;
import com.xinghe.cinema.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会员与优惠券。优惠券在订单落库时核销，实付金额已扣减，退票按实付金额算（文档 6.1）。
 */
@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final CouponMapper couponMapper;

    public MemberService(MemberMapper memberMapper, CouponMapper couponMapper) {
        this.memberMapper = memberMapper;
        this.couponMapper = couponMapper;
    }

    public Member getRequired(Long id) {
        Member m = memberMapper.selectById(id);
        if (m == null) {
            throw new BizException("会员不存在: " + id);
        }
        if (m.getStatus() != null && m.getStatus() == 0) {
            throw new BizException("会员已被禁用");
        }
        return m;
    }

    public Member create(Member member) {
        member.setId(null);
        if (member.getStatus() == null) {
            member.setStatus(1);
        }
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
        return member;
    }

    public List<Coupon> listCoupons(Long memberId) {
        return couponMapper.selectList(Wrappers.<Coupon>lambdaQuery()
                .eq(Coupon::getMemberId, memberId)
                .orderByAsc(Coupon::getStatus)
                .orderByDesc(Coupon::getId));
    }

    public Coupon issueCoupon(Coupon coupon) {
        coupon.setId(null);
        if (coupon.getStatus() == null) {
            coupon.setStatus(0);
        }
        coupon.setCreatedAt(LocalDateTime.now());
        couponMapper.insert(coupon);
        return coupon;
    }

    /**
     * 计算优惠券可抵扣金额。门槛按订单原始金额判断。
     *
     * @return 抵扣金额，0 表示不可用
     */
    public BigDecimal discount(Long memberId, Long couponId, BigDecimal amount) {
        Coupon c = couponMapper.selectById(couponId);
        if (c == null || !c.getMemberId().equals(memberId)) {
            throw new BizException("优惠券不存在");
        }
        if (c.getStatus() == null || c.getStatus() != 0) {
            throw new BizException("优惠券不可用");
        }
        if (c.getExpireAt() != null && c.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException("优惠券已过期");
        }
        if (c.getThreshold() != null && amount.compareTo(c.getThreshold()) < 0) {
            throw new BizException("订单金额未达到优惠券门槛 " + c.getThreshold());
        }
        BigDecimal d = c.getAmount() == null ? BigDecimal.ZERO : c.getAmount();
        return d.min(amount);
    }

    public void markCouponUsed(Long couponId, Long orderId) {
        if (couponId == null) {
            return;
        }
        if (couponMapper.markUsed(couponId, orderId) == 0) {
            throw new BizException("优惠券已被使用");
        }
    }

    public void releaseCoupon(Long couponId, Long orderId) {
        if (couponId == null) {
            return;
        }
        couponMapper.release(couponId, orderId);
    }
}
