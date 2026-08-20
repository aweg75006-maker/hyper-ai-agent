package com.yzz.hyperaiagent.demo.rag;

import com.yzz.hyperaiagent.gateway.application.GatewayChatModelFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MultiQueryExpanderDemo {

    private final ChatClient.Builder chatClientBuilder;

    public MultiQueryExpanderDemo(GatewayChatModelFactory gatewayChatModelFactory) {
        this.chatClientBuilder = ChatClient.builder(gatewayChatModelFactory.create("pdf-rag"));
    }

    public List<Query> expand(String query) {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .build();
        List<Query> queries = queryExpander.expand(new Query("XXXXXX")); // 这种方法效果不一定好
        return queries;
    }
}
