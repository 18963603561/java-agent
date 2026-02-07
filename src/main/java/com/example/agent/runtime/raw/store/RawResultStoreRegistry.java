package com.example.agent.runtime.raw.store;

import com.example.agent.runtime.raw.RawStoreType;
import com.example.agent.runtime.raw.ref.ParsedRawRef;
import com.example.agent.runtime.raw.ref.RawRefCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 原始存储路由注册表。
 *
 * <p>用途：根据存储类型统一路由到对应存储实现，隔离调用方对具体实现类的依赖。
 */
@Component
public class RawResultStoreRegistry {

    private final List<RawResultStore> stores;
    private final RawRefCodec rawRefCodec;

    public RawResultStoreRegistry(List<RawResultStore> stores, RawRefCodec rawRefCodec) {
        this.stores = stores == null ? List.of() : List.copyOf(stores);
        this.rawRefCodec = rawRefCodec;
    }

    /**
     * 根据存储类型获取存储实现。
     *
     * @param storeType 存储类型
     * @return 存储实现
     */
    public RawResultStore findRequired(RawStoreType storeType) {
        if (storeType == null) {
            throw new IllegalArgumentException("storeType 不能为空");
        }
        for (RawResultStore store : stores) {
            if (store != null && store.supports(storeType)) {
                return store;
            }
        }
        throw new IllegalStateException("未找到可用存储实现: " + storeType.getCode());
    }

    /**
     * 按统一引用读取文本。
     *
     * @param refId 统一引用
     * @return 文本内容，不存在返回 null
     */
    public String loadByRefId(String refId) {
        if (refId == null || refId.isBlank() || !rawRefCodec.isRawRef(refId)) {
            return null;
        }
        ParsedRawRef parsed = rawRefCodec.parse(refId);
        RawResultStore store = findRequired(parsed.storeType());
        return store.loadByStoreId(parsed.id());
    }

    /**
     * 获取已注册的存储类型列表。
     *
     * @return 存储类型列表
     */
    public List<String> supportedStoreTypes() {
        List<String> types = new ArrayList<>();
        for (RawResultStore store : stores) {
            if (store == null || store.storeType() == null) {
                continue;
            }
            String code = store.storeType().getCode();
            if (!types.contains(code)) {
                types.add(code);
            }
        }
        return types;
    }

    /**
     * 判断是否支持指定存储类型。
     *
     * @param storeType 存储类型
     * @return true 表示支持
     */
    public boolean supports(RawStoreType storeType) {
        return stores.stream().filter(Objects::nonNull).anyMatch(store -> store.supports(storeType));
    }
}

