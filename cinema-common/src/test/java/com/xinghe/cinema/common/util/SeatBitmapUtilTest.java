package com.xinghe.cinema.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 座位位运算的单测。这些公式是整套库存模型的地基（文档 1.2 / 3.2 / 3.4），必须锁死。
 */
class SeatBitmapUtilTest {

    @Test
    @DisplayName("offset 必须紧凑连续，不能用 seat.id")
    void offsetIsCompact() {
        // 10 列：第 3 排第 4 座 → (3-1)*10 + 3 = 23
        assertEquals(23, SeatBitmapUtil.offset(3, 4, 10));
        assertEquals(0, SeatBitmapUtil.offset(1, 1, 10));
        assertEquals(199, SeatBitmapUtil.offset(10, 20, 20));
    }

    @Test
    @DisplayName("文档 3.2 的例子：available=0b0111, need=3 → 从第 1 座起可连坐")
    void startsOfRunDocExample() {
        long available = 0b0111L;
        long starts = SeatBitmapUtil.startsOfRun(available, 3);
        assertEquals(0b0001L, starts);
    }

    @Test
    @DisplayName("中间空位可被识别，且不会越过已被占的座位")
    void startsOfRunMiddle() {
        // 座位 2,3,4,5 空（bit1..bit4），1 与 6 占用
        long available = 0b00011110L;
        long starts = SeatBitmapUtil.startsOfRun(available, 3);
        // 起点 bit1（座2）与 bit2（座3）都可行
        assertEquals(0b0110L, starts);
    }

    @Test
    @DisplayName("空位不足时返回 0")
    void startsOfRunNotEnough() {
        long available = 0b0101L; // 座1、座3 空，不连续
        assertEquals(0L, SeatBitmapUtil.startsOfRun(available, 2));
    }

    @Test
    @DisplayName("need=1 时等价于 available 本身")
    void startsOfRunSingle() {
        assertEquals(0b1010L, SeatBitmapUtil.startsOfRun(0b1010L, 1));
    }

    @Test
    @DisplayName("字节序：Redis bitmap 的 bit0 是首字节最高有效位")
    void bytesToWordsBitOrder() {
        long[] words = SeatBitmapUtil.bytesToWords(new byte[]{(byte) 0x80, 0x01}, 16);
        assertTrue(SeatBitmapUtil.isOccupied(words, 0));
        assertFalse(SeatBitmapUtil.isOccupied(words, 1));
        assertTrue(SeatBitmapUtil.isOccupied(words, 15));
    }

    @Test
    @DisplayName("跨 word 取排：base=63 的一排横跨两个 long")
    void extractRowAcrossWords() {
        // 整场 100 位，把 offset 63（第一 word 最高位）与 64（第二 word 最低位）置 1
        long[] words = new long[2];
        words[0] = 1L << 63;
        words[1] = 1L;

        // 某排 base = 63，colCount = 4 → 该排第 1 座（offset 63）与第 2 座（offset 64）被占
        long row = SeatBitmapUtil.extractRow(words, 63, 4);
        assertEquals(0b0011L, row);
    }

    @Test
    @DisplayName("行掩码屏蔽无效高位，colCount=64 时为全 1")
    void rowMask() {
        assertEquals(0b1111L, SeatBitmapUtil.rowMask(4));
        assertEquals(-1L, SeatBitmapUtil.rowMask(64));
    }

    @Test
    @DisplayName("位图展开成列号")
    void colsOf() {
        assertEquals(List.of(2, 3, 4), SeatBitmapUtil.colsOf(0b0001110L, 6));
    }

    @Test
    @DisplayName("offset 字符串与列表互转")
    void offsetsRoundTrip() {
        List<Integer> offsets = List.of(0, 5, 199);
        assertEquals(offsets, SeatBitmapUtil.parseOffsets(SeatBitmapUtil.joinOffsets(offsets)));
    }
}
