-- =====================================================================
-- 星河影城 · MySQL 8 建表脚本（与文档 1.4 一致，含文档未列出的补充字段）
-- 使用方式：
--   CREATE DATABASE cinema DEFAULT CHARACTER SET utf8mb4;
--   mysql -uroot -p cinema < db/schema-mysql.sql
--   然后以 --spring.profiles.active=mysql 启动
-- =====================================================================

CREATE TABLE IF NOT EXISTS cinema (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(64)  NOT NULL,
  city_code       VARCHAR(16)  NOT NULL,
  address         VARCHAR(255) NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1营业 0停业',
  cleanup_minutes INT          NOT NULL DEFAULT 15 COMMENT '场间清场时长',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS hall (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  cinema_id  BIGINT      NOT NULL,
  name       VARCHAR(32) NOT NULL COMMENT '如 1号厅 / IMAX厅',
  hall_type  TINYINT     NOT NULL DEFAULT 1 COMMENT '1普通 2情侣 3IMAX 4杜比',
  row_count  INT         NOT NULL,
  col_count  INT         NOT NULL,
  status     TINYINT     NOT NULL DEFAULT 1,
  KEY idx_cinema (cinema_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS seat (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  hall_id   BIGINT  NOT NULL,
  row_no    INT     NOT NULL,
  col_no    INT     NOT NULL,
  seat_type TINYINT NOT NULL DEFAULT 1 COMMENT '1普通 2情侣 3无障碍 4损坏',
  area_code CHAR(1) NOT NULL DEFAULT 'B' COMMENT '价格分区 A/B/C',
  UNIQUE KEY uk_hall_pos (hall_id, row_no, col_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS movie (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(64) NOT NULL,
  duration_minutes INT         NOT NULL COMMENT '片长，排片算 end_time 用',
  director         VARCHAR(64),
  actors           VARCHAR(255),
  poster_url       VARCHAR(255),
  release_date     DATE,
  status           TINYINT     NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `session` (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  cinema_id     BIGINT        NOT NULL,
  hall_id       BIGINT        NOT NULL,
  movie_id      BIGINT        NOT NULL,
  start_time    DATETIME      NOT NULL,
  end_time      DATETIME      NOT NULL COMMENT '已含清场时间的结束时间',
  base_price    DECIMAL(10,2) NOT NULL,
  price_version BIGINT        NOT NULL DEFAULT 1 COMMENT '绑定价格配置版本',
  status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1可售 0停售 -1已取消',
  rebuilding    TINYINT       NOT NULL DEFAULT 0 COMMENT 'bitmap 重建中，重建期间停售',
  KEY idx_hall_time (hall_id, start_time),
  KEY idx_movie_time (movie_id, start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS price_rule (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  version    BIGINT       NOT NULL,
  day_type   TINYINT      NOT NULL COMMENT '1平日 2周末 3法定节假日',
  time_slot  VARCHAR(16)  NOT NULL COMMENT 'MORNING/NOON/EVENING/MIDNIGHT',
  hall_type  TINYINT      NOT NULL,
  area_code  CHAR(1)      NOT NULL,
  multiplier DECIMAL(5,4) NOT NULL,
  UNIQUE KEY uk_rule (version, day_type, time_slot, hall_type, area_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `order` (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no       VARCHAR(32)   NOT NULL,
  member_id      BIGINT        NOT NULL,
  session_id     BIGINT        NOT NULL,
  seat_ids       VARCHAR(512)  NOT NULL COMMENT '逗号分隔，如 12,13,14',
  seat_labels    VARCHAR(512)  NOT NULL COMMENT '如 5排6座,5排7座，用于展示',
  total_amount   DECIMAL(10,2) NOT NULL,
  pay_amount     DECIMAL(10,2) NOT NULL,
  status         TINYINT       NOT NULL COMMENT '10待支付 20已支付 30已出票 40已观影 50退票中 60已退票 70已改签 90已取消',
  lock_expire_at DATETIME      NOT NULL COMMENT '锁座超时时间',
  version        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  request_id     VARCHAR(64)   NULL COMMENT '幂等键，文档 2.4 要求唯一索引',
  pay_no         VARCHAR(64)   NULL,
  paid_at        DATETIME      NULL,
  coupon_id      BIGINT        NULL,
  UNIQUE KEY uk_order_no (order_no),
  UNIQUE KEY uk_order_request (request_id),
  KEY idx_expire (status, lock_expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS order_item (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id   BIGINT        NOT NULL,
  session_id BIGINT        NOT NULL,
  seat_id    BIGINT        NOT NULL,
  seat_label VARCHAR(32)   NOT NULL,
  price      DECIMAL(10,2) NOT NULL COMMENT '价格快照',
  KEY idx_item_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS ticket (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT        NOT NULL,
  session_id    BIGINT        NOT NULL,
  seat_id       BIGINT        NOT NULL,
  price         DECIMAL(10,2) NOT NULL COMMENT '价格快照',
  verify_code   VARCHAR(16)   NOT NULL COMMENT '取票码',
  verify_status TINYINT       NOT NULL DEFAULT 0 COMMENT '0未核销 1已核销',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_session_seat (session_id, seat_id) COMMENT '最终防线：同一场次同一座位只能出一张票'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS payment (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  pay_no     VARCHAR(64)   NOT NULL,
  order_id   BIGINT        NOT NULL,
  order_no   VARCHAR(32)   NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  channel    VARCHAR(16)   NOT NULL DEFAULT 'MOCK',
  status     TINYINT       NOT NULL DEFAULT 0 COMMENT '0待支付 1成功 2失败 3关闭',
  paid_at    DATETIME      NULL,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pay_no (pay_no),
  KEY idx_pay_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS refund (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  refund_no   VARCHAR(64)   NOT NULL,
  order_id    BIGINT        NOT NULL,
  order_no    VARCHAR(32)   NOT NULL,
  amount      DECIMAL(10,2) NOT NULL,
  fee         DECIMAL(10,2) NOT NULL DEFAULT 0,
  reason      VARCHAR(255),
  status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2失败',
  refunded_at DATETIME      NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_refund_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS refund_policy (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  cinema_id    BIGINT       NULL COMMENT 'NULL 表示全局默认',
  hours_before INT          NOT NULL COMMENT '距开场小时数下限',
  fee_rate     DECIMAL(5,4) NOT NULL COMMENT '手续费率 0.1000 = 10%',
  enabled      TINYINT      NOT NULL DEFAULT 1
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS refund_record (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id      BIGINT        NOT NULL,
  order_no      VARCHAR(32)   NOT NULL,
  apply_amount  DECIMAL(10,2) NOT NULL,
  fee_rate      DECIMAL(5,4)  NOT NULL,
  fee           DECIMAL(10,2) NOT NULL,
  refund_amount DECIMAL(10,2) NOT NULL,
  reason        VARCHAR(255),
  status        TINYINT       NOT NULL DEFAULT 0,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_refund_record_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS change_record (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id        BIGINT        NOT NULL,
  order_no        VARCHAR(32)   NOT NULL,
  old_session_id  BIGINT        NOT NULL,
  old_seat_ids    VARCHAR(512)  NOT NULL,
  old_seat_labels VARCHAR(512)  NOT NULL,
  new_session_id  BIGINT        NOT NULL,
  new_seat_ids    VARCHAR(512)  NOT NULL,
  new_seat_labels VARCHAR(512)  NOT NULL,
  diff_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '新总价-旧总价',
  status          TINYINT       NOT NULL COMMENT '1成功 2待补款 3已回滚',
  old_released    TINYINT       NOT NULL DEFAULT 0 COMMENT '0未释放 1已释放',
  pay_expire_at   DATETIME      NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_change_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS member (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone      VARCHAR(20) NOT NULL,
  nickname   VARCHAR(64),
  status     TINYINT     NOT NULL DEFAULT 1,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_member_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS coupon (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id     BIGINT        NOT NULL,
  name          VARCHAR(64)   NOT NULL,
  amount        DECIMAL(10,2) NOT NULL,
  threshold     DECIMAL(10,2) NOT NULL DEFAULT 0,
  status        TINYINT       NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
  expire_at     DATETIME      NULL,
  used_order_id BIGINT        NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_coupon_member (member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS delay_task (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  topic      VARCHAR(64)  NOT NULL,
  payload    VARCHAR(512) NOT NULL,
  execute_at DATETIME     NOT NULL,
  status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0待执行 1已执行 2失败',
  retries    INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_delay_due (status, execute_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
