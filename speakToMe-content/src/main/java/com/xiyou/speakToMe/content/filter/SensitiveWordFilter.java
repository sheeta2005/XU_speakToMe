package com.xiyou.speakToMe.content.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiyou.speakToMe.content.entity.SensitiveWord;
import com.xiyou.speakToMe.content.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地敏感词过滤器（DFA / Trie 前缀树）。
 *
 * 匹配前先归一化：全角转半角、小写化、去除零宽字符与空格/标点，
 * 以命中"代 课""代*k*课"等变形写法。
 *
 * 词库启动时从 sensitive_word 表加载，运营补词后调用 reload() 重建（安全发布：volatile root）。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    /** Trie 节点 */
    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private Integer level;    // 1 直接拦截 / 2 人工复审
        private Integer category; // 词分类
    }

    /** 命中结果 */
    public record Hit(String word, int level, int category) {
    }

    /** 匹配结果 */
    public static class MatchResult {
        private final List<Hit> hits;

        MatchResult(List<Hit> hits) {
            this.hits = hits;
        }

        public boolean hasLevel1() {
            return hits.stream().anyMatch(h -> h.level() == 1);
        }

        public boolean hasLevel2() {
            return hits.stream().anyMatch(h -> h.level() == 2);
        }

        /** 首个命中词（日志记录用） */
        public String firstHitWord() {
            return hits.isEmpty() ? null : hits.get(0).word();
        }

        public List<Hit> getHits() {
            return hits;
        }
    }

    private final SensitiveWordMapper sensitiveWordMapper;

    private volatile TrieNode root = new TrieNode();

    public SensitiveWordFilter(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从表加载启用词库并重建 Trie */
    public void reload() {
        List<SensitiveWord> words = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getStatus, 1));
        TrieNode newRoot = new TrieNode();
        for (SensitiveWord w : words) {
            insert(newRoot, w.getWord(), w.getLevel(), w.getCategory());
        }
        this.root = newRoot;
        log.info("敏感词库加载完成，共 {} 条", words.size());
    }

    private void insert(TrieNode root, String word, int level, int category) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.level = level;
        node.category = category;
    }

    /** 文本匹配，返回全部命中（含权重等级） */
    public MatchResult match(String text) {
        if (text == null || text.isBlank()) {
            return new MatchResult(List.of());
        }
        String norm = normalize(text);
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < norm.length(); i++) {
            TrieNode node = root;
            for (int j = i; j < norm.length(); j++) {
                node = node.children.get(norm.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.level != null) {
                    hits.add(new Hit(norm.substring(i, j + 1), node.level, node.category));
                }
            }
        }
        return new MatchResult(deduplicate(hits));
    }

    /** 同词多命中只保留最高等级 */
    private List<Hit> deduplicate(List<Hit> hits) {
        Map<String, Hit> map = new HashMap<>();
        for (Hit h : hits) {
            Hit exist = map.get(h.word());
            if (exist == null || h.level() > exist.level()) {
                map.put(h.word(), h);
            }
        }
        return new ArrayList<>(map.values());
    }

    /** 归一化：半角化、小写、去零宽与空白标点（仅用于匹配，不回写原文） */
    public String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\u3000') {                      // 全角空格
                continue;
            }
            if (c >= '\uFF01' && c <= '\uFF5E') {     // 全角字符转半角
                c = (char) (c - 0xFEE0);
            }
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') {
                continue;                             // 零宽字符
            }
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            if (c <= 127 && !Character.isLetterOrDigit(c)) {
                continue;                             // 去 ASCII 标点/符号（变形词兜底）
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
