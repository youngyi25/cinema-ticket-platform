package com.xinghe.cinema.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 座位位运算工具（文档 1.2 / 3.2 / 3.4）。
 *
 * <p>约定：offset = (rowNo - 1) * colCount + (colNo - 1)，即整场紧凑连续排列；
 * 一排映射到一个 long，bit i = 1 表示该排第 i+1 个座位「不可售」。
 * 单排上限 64 座，超出走降级路径（逐位实时计算）。</p>
 */
public final class SeatBitmapUtil {

    /** 单 word（long）可承载的最大座位数。 */
    public static final int MAX_COL_PER_WORD = 64;

    private SeatBitmapUtil() {
    }

    /** 座位偏移量。这是唯一正确的写法，禁止直接用 seat.id。 */
    public static int offset(int rowNo, int colNo, int colCount) {
        return (rowNo - 1) * colCount + (colNo - 1);
    }

    /** 行掩码，屏蔽掉无效高位；colCount=64 时返回全 1。 */
    public static long rowMask(int colCount) {
        if (colCount >= MAX_COL_PER_WORD) {
            return -1L;
        }
        return (1L << colCount) - 1;
    }

    /**
     * 原始字节转 long[]。Redis bitmap 的 bit0 是首字节的最高有效位。
     *
     * @param bitLength 有效位数（rowCount * colCount）
     */
    public static long[] bytesToWords(byte[] raw, int bitLength) {
        int wordCount = Math.max(1, (bitLength + 63) / 64);
        long[] words = new long[wordCount];
        if (raw == null || raw.length == 0) {
            return words;
        }
        int limit = Math.min(raw.length * 8, bitLength);
        for (int i = 0; i < limit; i++) {
            if ((raw[i >> 3] & (0x80 >>> (i & 7))) != 0) {
                words[i >>> 6] |= (1L << (i & 63));
            }
        }
        return words;
    }

    /**
     * 从整场 word 数组中抽取某一排的位图。排区间可能跨 word，需要拼接。
     *
     * @param base    该排起始 offset
     * @param colCount 该排座位数
     */
    public static long extractRow(long[] words, int base, int colCount) {
        int wordIdx = base >>> 6;
        int shift = base & 63;
        long v = wordIdx < words.length ? (words[wordIdx] >>> shift) : 0L;
        if (shift != 0 && wordIdx + 1 < words.length) {
            v |= words[wordIdx + 1] << (64 - shift);
        }
        return v & rowMask(colCount);
    }

    /**
     * 求「从某位开始连续 need 个空位」的起始位集合（文档 3.2）。
     * 返回值的 bit i = 1 表示从第 i 个座位起连续 need 个可用。复杂度 O(need)。
     *
     * @param available bit=1 表示该座位可售
     */
    public static long startsOfRun(long available, int need) {
        if (need <= 0) {
            return 0L;
        }
        long run = available;
        for (int i = 1; i < need; i++) {
            run &= (available >>> i);
        }
        return run;
    }

    /** 把位图展开成 1-based 列号列表。 */
    public static List<Integer> colsOf(long bits, int colCount) {
        List<Integer> cols = new ArrayList<>();
        for (int i = 0; i < colCount; i++) {
            if ((bits & (1L << i)) != 0) {
                cols.add(i + 1);
            }
        }
        return cols;
    }

    /** 判断某个 offset 是否不可售。 */
    public static boolean isOccupied(long[] words, int offset) {
        int wordIdx = offset >>> 6;
        if (wordIdx >= words.length) {
            return false;
        }
        return (words[wordIdx] & (1L << (offset & 63))) != 0;
    }

    /** 逗号分隔字符串 → offset 列表。 */
    public static List<Integer> parseOffsets(String csv) {
        List<Integer> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return list;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                list.add(Integer.parseInt(t));
            }
        }
        return list;
    }

    /** offset 列表 → 逗号分隔字符串。 */
    public static String joinOffsets(List<Integer> offsets) {
        StringBuilder sb = new StringBuilder();
        for (Integer o : offsets) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(o);
        }
        return sb.toString();
    }
}
