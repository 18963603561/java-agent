package com.example.agent.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 观察窗口缓冲区，按条数保留最近观察。
 */
public class ObservationWindowBuffer {

    private final int maxSize;
    private final Deque<ReactObservation> buffer = new ArrayDeque<>();

    public ObservationWindowBuffer(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    /**
     * 添加观察记录并裁剪窗口。
     *
     * @param observation 观察记录
     */
    public void add(ReactObservation observation) {
        if (observation == null) {
            return;
        }
        buffer.addLast(observation);
        while (buffer.size() > maxSize) {
            buffer.removeFirst();
        }
    }

    /**
     * 获取当前窗口内观察记录。
     *
     * @return 观察列表
     */
    public List<ReactObservation> snapshot() {
        return new ArrayList<>(buffer);
    }

    /**
     * 获取当前窗口大小。
     *
     * @return 当前窗口内的记录数量
     */
    public int size() {
        return buffer.size();
    }
}
