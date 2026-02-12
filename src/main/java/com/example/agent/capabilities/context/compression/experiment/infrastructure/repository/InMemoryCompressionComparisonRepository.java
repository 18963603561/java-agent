package com.example.agent.capabilities.context.compression.experiment.infrastructure.repository;

import com.example.agent.capabilities.context.compression.experiment.domain.model.CompressionComparisonRecord;
import com.example.agent.capabilities.context.compression.experiment.domain.port.CompressionComparisonRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

/**
 * 基于内存的压缩对比记录仓储实现。
 */
@Repository
public class InMemoryCompressionComparisonRepository implements CompressionComparisonRepository {

    /**
     * 对比记录内存存储。
     */
    private final CopyOnWriteArrayList<CompressionComparisonRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void save(CompressionComparisonRecord record) {
        // 参数校验：空记录不写入，避免污染审计数据。
        if (record == null) {
            return;
        }
        records.add(record);
    }

    /**
     * 返回只读记录快照。
     */
    public List<CompressionComparisonRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /**
     * 清空记录。
     */
    public void clear() {
        records.clear();
    }
}
