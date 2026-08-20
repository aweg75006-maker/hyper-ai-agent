package com.yzz.hyperaiagent.gateway.infrastructure.provider;

/**
 * Provider 流的内部事件。
 *
 * <p>content 事件代表已经产生可见内容；terminal 事件只负责携带结束原因和最终 Usage。</p>
 */
public record ProviderStreamChunk(
        String content,
        boolean terminal,
        String finishReason,
        ProviderUsage usage
) {
    public static ProviderStreamChunk content(String content) {
        return new ProviderStreamChunk(content, false, null, null);
    }

    public static ProviderStreamChunk terminal(String finishReason, ProviderUsage usage) {
        return new ProviderStreamChunk(null, true, finishReason, usage);
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
