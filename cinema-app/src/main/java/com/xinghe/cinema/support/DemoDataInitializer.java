package com.xinghe.cinema.support;

import com.xinghe.cinema.cinema.dto.HallCreateRequest;
import com.xinghe.cinema.cinema.entity.Cinema;
import com.xinghe.cinema.cinema.entity.Hall;
import com.xinghe.cinema.cinema.service.CinemaService;
import com.xinghe.cinema.cinema.service.HallService;
import com.xinghe.cinema.member.entity.Coupon;
import com.xinghe.cinema.member.entity.Member;
import com.xinghe.cinema.member.service.MemberService;
import com.xinghe.cinema.movie.entity.Movie;
import com.xinghe.cinema.movie.service.MovieService;
import com.xinghe.cinema.order.entity.RefundPolicy;
import com.xinghe.cinema.order.mapper.RefundPolicyMapper;
import com.xinghe.cinema.schedule.dto.CreateSessionRequest;
import com.xinghe.cinema.schedule.entity.PriceRule;
import com.xinghe.cinema.schedule.entity.Session;
import com.xinghe.cinema.schedule.service.PriceRuleService;
import com.xinghe.cinema.schedule.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据装配。
 *
 * <p>H2 是内存库，每次启动都会重建表，因此这里用服务层接口造数据 ——
 * 好处是顺带验证了「建厅生成座位」「排片冲突检测」这些真实代码路径。</p>
 */
@Slf4j
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final CinemaService cinemaService;
    private final HallService hallService;
    private final MovieService movieService;
    private final PriceRuleService priceRuleService;
    private final SessionService sessionService;
    private final MemberService memberService;
    private final RefundPolicyMapper refundPolicyMapper;

    @Value("${cinema.demo-data.enabled:true}")
    private boolean enabled;

    public DemoDataInitializer(CinemaService cinemaService, HallService hallService, MovieService movieService,
                               PriceRuleService priceRuleService, SessionService sessionService,
                               MemberService memberService, RefundPolicyMapper refundPolicyMapper) {
        this.cinemaService = cinemaService;
        this.hallService = hallService;
        this.movieService = movieService;
        this.priceRuleService = priceRuleService;
        this.sessionService = sessionService;
        this.memberService = memberService;
        this.refundPolicyMapper = refundPolicyMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (!cinemaService.listAll().isEmpty()) {
            log.info("已存在影院数据，跳过演示数据初始化");
            return;
        }

        long priceVersion = seedPriceRules();
        seedRefundPolicies();

        Cinema cinema = new Cinema();
        cinema.setName("星河影城（红谷滩店）");
        cinema.setCityCode("360100");
        cinema.setAddress("江西省南昌市红谷滩区星河路 1 号");
        cinema.setCleanupMinutes(15);
        cinemaService.create(cinema);

        Hall normal = createHall(cinema.getId(), "1号厅", 1, 10, 20);
        Hall imax = createHall(cinema.getId(), "IMAX厅", 3, 15, 24);
        Hall couple = createHall(cinema.getId(), "情侣厅", 2, 8, 12);

        Movie m1 = createMovie("流浪地球3", 130, "郭帆");
        Movie m2 = createMovie("深海回响", 110, "陈思");
        Movie m3 = createMovie("长安十二时辰", 120, "曹盾");

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Session> sessions = new ArrayList<>();
        sessions.add(createSession(cinema.getId(), normal.getId(), m1.getId(), tomorrow, LocalTime.of(19, 30), "45.00", priceVersion));
        sessions.add(createSession(cinema.getId(), normal.getId(), m2.getId(), tomorrow, LocalTime.of(14, 0), "42.00", priceVersion));
        sessions.add(createSession(cinema.getId(), imax.getId(), m1.getId(), tomorrow, LocalTime.of(20, 0), "68.00", priceVersion));
        sessions.add(createSession(cinema.getId(), imax.getId(), m3.getId(), tomorrow, LocalTime.of(10, 30), "58.00", priceVersion));
        sessions.add(createSession(cinema.getId(), couple.getId(), m2.getId(), tomorrow, LocalTime.of(16, 0), "52.00", priceVersion));

        Member member = new Member();
        member.setPhone("13800000001");
        member.setNickname("杨毅");
        memberService.create(member);

        Coupon coupon = new Coupon();
        coupon.setMemberId(member.getId());
        coupon.setName("满100减20");
        coupon.setAmount(new BigDecimal("20.00"));
        coupon.setThreshold(new BigDecimal("100.00"));
        coupon.setExpireAt(LocalDateTime.now().plusDays(30));
        memberService.issueCoupon(coupon);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cinemaId", cinema.getId());
        info.put("hallIds", List.of(normal.getId(), imax.getId(), couple.getId()));
        info.put("movieIds", List.of(m1.getId(), m2.getId(), m3.getId()));
        info.put("sessionIds", sessions.stream().map(Session::getId).toList());
        info.put("memberId", member.getId());
        info.put("couponId", coupon.getId());
        info.put("priceVersion", priceVersion);
        log.info("演示数据初始化完成 {}", info);
        log.info("座位总数：1号厅 {}，IMAX厅 {}，情侣厅 {}",
                normal.seatCount(), imax.seatCount(), couple.seatCount());
        log.info("快速体验：GET /session/{}/seatmap → POST /order/lock → POST /payment/{{orderNo}}/pay",
                sessions.get(0).getId());
    }

    /** 价格规则 v1：3 日型 × 4 时段 × 3 厅型 × 3 分区 = 108 条。 */
    private long seedPriceRules() {
        Map<String, BigDecimal> slotFactor = Map.of(
                "MIDNIGHT", new BigDecimal("0.80"),
                "MORNING", new BigDecimal("0.85"),
                "NOON", BigDecimal.ONE,
                "EVENING", new BigDecimal("1.20"));
        Map<Integer, BigDecimal> dayFactor = Map.of(
                1, BigDecimal.ONE,
                2, new BigDecimal("1.10"),
                3, new BigDecimal("1.20"));
        Map<Integer, BigDecimal> hallFactor = Map.of(
                1, BigDecimal.ONE,
                2, new BigDecimal("1.30"),
                3, new BigDecimal("1.50"));
        Map<String, BigDecimal> areaFactor = Map.of(
                "A", new BigDecimal("0.85"),
                "B", BigDecimal.ONE,
                "C", new BigDecimal("0.80"));

        List<PriceRule> rules = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> day : dayFactor.entrySet()) {
            for (Map.Entry<String, BigDecimal> slot : slotFactor.entrySet()) {
                for (Map.Entry<Integer, BigDecimal> hall : hallFactor.entrySet()) {
                    for (Map.Entry<String, BigDecimal> area : areaFactor.entrySet()) {
                        PriceRule rule = new PriceRule();
                        rule.setDayType(day.getKey());
                        rule.setTimeSlot(slot.getKey());
                        rule.setHallType(hall.getKey());
                        rule.setAreaCode(area.getKey());
                        rule.setMultiplier(day.getValue()
                                .multiply(slot.getValue())
                                .multiply(hall.getValue())
                                .multiply(area.getValue())
                                .setScale(4, RoundingMode.HALF_UP));
                        rules.add(rule);
                    }
                }
            }
        }
        return priceRuleService.createVersion(rules);
    }

    /** 退票阶梯（文档 6.1）：>24h 免费、2~24h 10%、0~2h 30%。 */
    private void seedRefundPolicies() {
        insertPolicy(24, "0.0000");
        insertPolicy(2, "0.1000");
        insertPolicy(0, "0.3000");
    }

    private void insertPolicy(int hoursBefore, String rate) {
        RefundPolicy p = new RefundPolicy();
        p.setCinemaId(null);
        p.setHoursBefore(hoursBefore);
        p.setFeeRate(new BigDecimal(rate));
        p.setEnabled(1);
        refundPolicyMapper.insert(p);
    }

    private Hall createHall(Long cinemaId, String name, int hallType, int rows, int cols) {
        HallCreateRequest req = new HallCreateRequest();
        req.setCinemaId(cinemaId);
        req.setName(name);
        req.setHallType(hallType);
        req.setRowCount(rows);
        req.setColCount(cols);
        return hallService.createHall(req);
    }

    private Movie createMovie(String name, int duration, String director) {
        Movie m = new Movie();
        m.setName(name);
        m.setDurationMinutes(duration);
        m.setDirector(director);
        m.setActors("—");
        m.setReleaseDate(LocalDate.now().minusDays(7));
        return movieService.create(m);
    }

    private Session createSession(Long cinemaId, Long hallId, Long movieId, LocalDate date,
                                  LocalTime time, String basePrice, long priceVersion) {
        CreateSessionRequest req = new CreateSessionRequest();
        req.setCinemaId(cinemaId);
        req.setHallId(hallId);
        req.setMovieId(movieId);
        req.setStartTime(LocalDateTime.of(date, time));
        req.setBasePrice(new BigDecimal(basePrice));
        req.setPriceVersion(priceVersion);
        return sessionService.create(req);
    }
}
