package com.example.agent.runtime.summary;

import java.util.List;

/**
 * 摘要计算模型集合。
 *
 * <p>用途：承载摘要计算过程中的限制配置、截断状态、快照结果与写入器对象。
 * <p>输入输出：由摘要构建链路在各组件之间传递使用。
 * <p>边界：本类仅承载数据，不包含业务流程。
 */
public final class SummaryComputationModels {

    private SummaryComputationModels() {
    }

    /**
     * 摘要限制。
     */
    public static final class SummaryLimits {

        /**
         * 最大总字符数。
         */
        private final int maxChars;

        /**
         * 最大条目数。
         */
        private final int maxListItems;

        /**
         * 单字段最大字符数。
         */
        private final int maxFieldChars;

        public SummaryLimits(int maxChars, int maxListItems, int maxFieldChars) {
            this.maxChars = maxChars;
            this.maxListItems = maxListItems;
            this.maxFieldChars = maxFieldChars;
        }

        /**
         * 由配置构建限制。
         *
         * @param properties 配置
         * @return 限制
         */
        public static SummaryLimits from(StepSummaryProperties properties) {
            if (properties == null) {
                return new SummaryLimits(0, 0, 0);
            }
            return new SummaryLimits(properties.getMaxChars(),
                    properties.getMaxListItems(),
                    properties.getMaxFieldChars());
        }

        public int getMaxChars() {
            return maxChars;
        }

        public int getMaxListItems() {
            return maxListItems;
        }

        public int getMaxFieldChars() {
            return maxFieldChars;
        }
    }

    /**
     * 输出快照。
     */
    public static final class OutputSnapshot {

        private final int keyCount;
        private final List<String> keys;
        private final int charCount;
        private final String sample;

        public OutputSnapshot(int keyCount, List<String> keys, int charCount, String sample) {
            this.keyCount = keyCount;
            this.keys = keys;
            this.charCount = charCount;
            this.sample = sample;
        }

        public int getKeyCount() {
            return keyCount;
        }

        public List<String> getKeys() {
            return keys;
        }

        public int getCharCount() {
            return charCount;
        }

        public String getSample() {
            return sample;
        }
    }

    /**
     * 键快照。
     */
    public static final class KeySnapshot {

        private final int keyCount;
        private final List<String> keys;

        public KeySnapshot(int keyCount, List<String> keys) {
            this.keyCount = keyCount;
            this.keys = keys;
        }

        public int getKeyCount() {
            return keyCount;
        }

        public List<String> getKeys() {
            return keys;
        }
    }

    /**
     * 截断状态。
     */
    public static final class TruncationState {

        /**
         * 是否截断。
         */
        private boolean truncated;

        /**
         * 标记截断。
         */
        public void markTruncated() {
            this.truncated = true;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    /**
     * 有界文本快照。
     */
    public static final class BoundedSnapshot {

        private final String text;
        private final int charCount;

        public BoundedSnapshot(String text, int charCount) {
            this.text = text;
            this.charCount = charCount;
        }

        public String getText() {
            return text;
        }

        public int getCharCount() {
            return charCount;
        }
    }

    /**
     * 快照写入器。
     */
    public static final class SnapshotWriter {

        /**
         * 缓冲区。
         */
        private final StringBuilder builder = new StringBuilder();

        /**
         * 最大字符数。
         */
        private final int maxChars;

        /**
         * 截断状态。
         */
        private final TruncationState truncation;

        /**
         * 是否停止。
         */
        private boolean stopped;

        public SnapshotWriter(int maxChars, TruncationState truncation) {
            this.maxChars = maxChars;
            this.truncation = truncation;
        }

        /**
         * 追加文本。
         *
         * @param text 文本
         */
        public void append(String text) {
            if (stopped || text == null) {
                return;
            }
            if (maxChars > 0) {
                int remaining = maxChars - builder.length();
                if (remaining <= 0) {
                    truncation.markTruncated();
                    stopped = true;
                    return;
                }
                if (text.length() > remaining) {
                    builder.append(text, 0, remaining);
                    truncation.markTruncated();
                    stopped = true;
                    return;
                }
            }
            builder.append(text);
        }

        public boolean isStopped() {
            return stopped;
        }

        public int length() {
            return builder.length();
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
