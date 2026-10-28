-- =====================================================================
-- 星河影城 · H2 (MySQL 兼容模式) 建表脚本
-- 用于本地零依赖启动：每次启动重建，数据只用内存库
-- MySQL 版本见 db/schema-mysql.sql
-- =====================================================================

DROP TABLE IF EXISTS ticket;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS change_record;
DROP TABLE IF EXISTS refund_record;
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS refund;
DROP TABLE IF EXISTS coupon;
DROP TABLE IF EXISTS member;
DROP TABLE IF EXISTS `session`;
DROP TABLE IF EXISTS price_rule;
DROP TABLE IF EXISTS refund_policy;
DROP TABLE IF EXISTS seat;
DROP TABLE IF EXISTS hall;
DROP TABLE IF EXISTS movie;
DROP TABLE IF EXISTS cinema;
DROP TABLE IF EXISTS delay_task;

CREATE TABLE cinema (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(64)  NOT NULL,
  city_code       VARCHAR(16)  NOT NULL,
  address         VARCHAR(255) NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  cleanup_minutes INT          NOT NULL DEFAULT 15,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hall (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  cinema_id  BIGINT      NOT NULL,
  name       VARCHAR(32) NOT NULL,
  hall_type  TINYINT     NOT NULL DEFAULT 1,
  row_count  INT         NOT NULL,
  col_count  INT         NOT NULL,
  status     TINYINT     NOT NULL DEFAULT 1
);
CREATE INDEX idx_hall_cinema ON hall (cinema_id);

CREATE TABLE seat (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  hall_id   BIGINT  NOT NULL,
  row_no    INT     NOT NULL,
  col_no    INT     NOT NULL,
  seat_type TINYINT NOT NULL DEFAULT 1,
  area_code CHAR(1) NOT NULL DEFAULT 'B'
);
CREATE UNIQUE INDEX uk_hall_pos ON seat (hall_id, row_no, col_no);

CREATE TABLE movie (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  name             VARCHAR(64) NOT NULL,
  duration_minutes INT         NOT NULL,
  director         VARCHAR(64),
  actors           VARCHAR(255),
  poster_url       VARCHAR(255),
  release_date     DATE,
  status           TINYINT     NOT NULL DEFAULT 1,
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `session` (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  cinema_id     BIGINT        NOT NULL,
  hall_id       BIGINT        NOT NULL,
  movie_id      BIGINT        NOT NULL,
  start_time    DATETIME      NOT NULL,
  end_time      DATETIME      NOT NULL,
  base_price    DECIMAL(10,2) NOT NULL,
  price_version BIGINT        NOT NULL DEFAULT 1,
  status        TINYINT       NOT NULL DEFAULT 1,
  rebuilding    TINYINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_hall_time ON `session` (hall_id, start_time);
CREATE INDEX idx_movie_time ON `session` (movie_id, start_time);

CREATE TABLE price_rule (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  version    BIGINT        NOT NULL,
  day_type   TINYINT       NOT NULL,
  time_slot  VARCHAR(16)   NOT NULL,
  hall_type  TINYINT       NOT NULL,
  area_code  CHAR(1)       NOT NULL,
  multiplier DECIMAL(5,4)  NOT NULL
);
CREATE UNIQUE INDEX uk_rule ON price_rule (version, day_type, time_slot, hall_type, area_code);

CREATE TABLE `order` (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no       VARCHAR(32)   NOT NULL,
  member_id      BIGINT        NOT NULL,
  session_id     BIGINT        NOT NULL,
  seat_ids       VARCHAR(512)  NOT NULL,
  seat_labels    VARCHAR(512)  NOT NULL,
  total_amount   DECIMAL(10,2) NOT NULL,
  pay_amount     DECIMAL(10,2) NOT NULL,
  status         TINYINT       NOT NULL,
  lock_expire_at DATETIME      NOT NULL,
  version        INT           NOT NULL DEFAULT 0,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  request_id     VARCHAR(64),
  pay_no         VARCHAR(64),
  paid_at        DATETIME,
  coupon_id      BIGINT
);
CREATE UNIQUE INDEX uk_order_no ON `order` (order_no);
CREATE UNIQUE INDEX uk_order_request ON `order` (request_id);
CREATE INDEX idx_expire ON `order` (status, lock_expire_at);

CREATE TABLE order_item (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id   BIGINT        NOT NULL,
  session_id BIGINT        NOT NULL,
  seat_id    BIGINT        NOT NULL,
  seat_label VARCHAR(32)   NOT NULL,
  price      DECIMAL(10,2) NOT NULL
);
CREATE INDEX idx_item_order ON order_item (order_id);

CREATE TABLE ticket (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id      BIGINT        NOT NULL,
  session_id    BIGINT        NOT NULL,
  seat_id       BIGINT        NOT NULL,
  price         DECIMAL(10,2) NOT NULL,
  verify_code   VARCHAR(16)   NOT NULL,
  verify_status TINYINT       NOT NULL DEFAULT 0,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_session_seat ON ticket (session_id, seat_id);

CREATE TABLE payment (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  pay_no     VARCHAR(64)   NOT NULL,
  order_id   BIGINT        NOT NULL,
  order_no   VARCHAR(32)   NOT NULL,
  amount     DECIMAL(10,2) NOT NULL,
  channel    VARCHAR(16)   NOT NULL DEFAULT 'MOCK',
  status     TINYINT       NOT NULL DEFAULT 0,
  paid_at    DATETIME,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_pay_no ON payment (pay_no);
CREATE INDEX idx_pay_order ON payment (order_no);

CREATE TABLE refund (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  refund_no   VARCHAR(64)   NOT NULL,
  order_id    BIGINT        NOT NULL,
  order_no    VARCHAR(32)   NOT NULL,
  amount      DECIMAL(10,2) NOT NULL,
  fee         DECIMAL(10,2) NOT NULL DEFAULT 0,
  reason      VARCHAR(255),
  status      TINYINT       NOT NULL DEFAULT 0,
  refunded_at DATETIME,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refund_order ON refund (order_no);

CREATE TABLE refund_policy (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  cinema_id    BIGINT,
  hours_before INT          NOT NULL,
  fee_rate     DECIMAL(5,4) NOT NULL,
  enabled      TINYINT      NOT NULL DEFAULT 1
);

CREATE TABLE refund_record (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id      BIGINT        NOT NULL,
  order_no      VARCHAR(32)   NOT NULL,
  apply_amount  DECIMAL(10,2) NOT NULL,
  fee_rate      DECIMAL(5,4)  NOT NULL,
  fee           DECIMAL(10,2) NOT NULL,
  refund_amount DECIMAL(10,2) NOT NULL,
  reason        VARCHAR(255),
  status        TINYINT       NOT NULL DEFAULT 0,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refund_record_order ON refund_record (order_no);

CREATE TABLE change_record (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id       BIGINT        NOT NULL,
  order_no       VARCHAR(32)   NOT NULL,
  old_session_id BIGINT        NOT NULL,
  old_seat_ids   VARCHAR(512)  NOT NULL,
  old_seat_labels VARCHAR(512) NOT NULL,
  new_session_id BIGINT        NOT NULL,
  new_seat_ids   VARCHAR(512)  NOT NULL,
  new_seat_labels VARCHAR(512) NOT NULL,
  diff_amount    DECIMAL(10,2) NOT NULL DEFAULT 0,
  status         TINYINT       NOT NULL,
  old_released   TINYINT       NOT NULL DEFAULT 0,
  pay_expire_at  DATETIME,
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_change_order ON change_record (order_id);

CREATE TABLE member (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone      VARCHAR(20) NOT NULL,
  nickname   VARCHAR(64),
  status     TINYINT     NOT NULL DEFAULT 1,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_member_phone ON member (phone);

CREATE TABLE coupon (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  member_id     BIGINT        NOT NULL,
  name          VARCHAR(64)   NOT NULL,
  amount        DECIMAL(10,2) NOT NULL,
  threshold     DECIMAL(10,2) NOT NULL DEFAULT 0,
  status        TINYINT       NOT NULL DEFAULT 0,
  expire_at     DATETIME,
  used_order_id BIGINT,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_coupon_member ON coupon (member_id);

CREATE TABLE delay_task (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  topic      VARCHAR(64)  NOT NULL,
  payload    VARCHAR(512) NOT NULL,
  execute_at DATETIME     NOT NULL,
  status     TINYINT      NOT NULL DEFAULT 0,
  retries    INT          NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_delay_due ON delay_task (status, execute_at);
