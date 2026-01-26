package com.example.agent.research;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 深度研究流程，负责组织检索与引用输出。
 */
@Service
public class ResearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipeline.class);

    public List<ResearchCitation> run(String query) {
        log.info("研究流程启动, queryLength={}", query == null ? 0 : query.length());
        return List.of();
    }
}
