package nvc.guide.modules.nvcvoice.service;

import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NvcVoicePromptService 语音 Prompt 服务")
class NvcVoicePromptServiceTest {

    private NvcVoicePromptService service;

    @BeforeEach
    void setUp() {
        NvcVoiceProperties properties = new NvcVoiceProperties();
        properties.setAiQuestionMaxChars(120);
        service = new NvcVoicePromptService(properties);
    }

    // ==================== buildSystemPrompt ====================

    @Nested
    @DisplayName("buildSystemPrompt 系统 Prompt 构建")
    class BuildSystemPromptTests {

        @Test
        @DisplayName("包含 Agent 角色设定 + 语音约束 + 反注入")
        void buildSystemPrompt_withAgentPrompt_containsAll() {
            String result = service.buildSystemPrompt("你是NVC教练", null);
            assertTrue(result.contains("你是NVC教练"));
            assertTrue(result.contains("语音输出要求"));
            assertTrue(result.contains("安全边界"));
        }

        @Test
        @DisplayName("包含场景描述")
        void buildSystemPrompt_withScenario_containsScenario() {
            String result = service.buildSystemPrompt("你是NVC教练", "与同事发生冲突");
            assertTrue(result.contains("与同事发生冲突"));
            assertTrue(result.contains("练习场景"));
        }

        @Test
        @DisplayName("null Agent Prompt 不崩溃")
        void buildSystemPrompt_nullAgentPrompt_noCrash() {
            String result = service.buildSystemPrompt(null, null);
            assertNotNull(result);
            assertTrue(result.contains("语音输出要求"));
        }

        @Test
        @DisplayName("空场景描述不包含场景段")
        void buildSystemPrompt_emptyScenario_noScenarioSection() {
            String result = service.buildSystemPrompt("你是NVC教练", "");
            assertFalse(result.contains("练习场景"));
        }
    }

    // ==================== normalizeRealtimeText ====================

    @Nested
        @DisplayName("normalizeRealtimeText 文本标准化")
    class NormalizeTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("null/空白输入返回空字符串")
        void normalize_nullOrBlank_returnsEmpty(String input) {
            assertEquals("", service.normalizeRealtimeText(input));
        }

        @Test
        @DisplayName("去除 markdown 加粗")
        void normalize_removesBold() {
            assertEquals("你好世界", service.normalizeRealtimeText("**你好**世界"));
        }

        @Test
        @DisplayName("去除代码块")
        void normalize_removesCodeBlock() {
            assertEquals("code here", service.normalizeRealtimeText("```code here```"));
        }

        @Test
        @DisplayName("去除行内代码")
        void normalize_removesInlineCode() {
            assertEquals("hello", service.normalizeRealtimeText("`hello`"));
        }

        @Test
        @DisplayName("去除列表标记")
        void normalize_removesListMarkers() {
            assertEquals("item1 item2", service.normalizeRealtimeText("- item1\n- item2"));
        }

        @Test
        @DisplayName("合并多余空白")
        void normalize_collapsesWhitespace() {
            assertEquals("hello world", service.normalizeRealtimeText("hello   world"));
        }
    }

    // ==================== optimizeForVoice ====================

    @Nested
        @DisplayName("optimizeForVoice 语音优化")
    class OptimizeTests {

        @Test
        @DisplayName("短文本直接返回")
        void optimize_shortText_returnsAsIs() {
            assertEquals("你好", service.optimizeForVoice("你好"));
        }

        @Test
        @DisplayName("空白文本返回默认提示")
        void optimize_blankText_returnsDefault() {
            assertEquals("请继续。", service.optimizeForVoice("   "));
        }

        @Test
        @DisplayName("超长文本在句子边界截断")
        void optimize_longText_truncatesAtSentenceBoundary() {
            // 构造有句子边界的长文本：先有足够多的句子标点
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("这是第").append(i).append("句话的内容。");
            }
            String result = service.optimizeForVoice(sb.toString());
            assertTrue(result.length() <= 120, "长度应 <= 120，实际: " + result.length());
        }

        @Test
        @DisplayName("超长文本无句子边界时加省略号")
        void optimize_longTextNoSentenceEnd_addsEllipsis() {
            String longText = "很长的内容".repeat(30);
            String result = service.optimizeForVoice(longText);
            assertTrue(result.endsWith("…"));
            assertTrue(result.length() <= 121); // 120 + …
        }
    }

    // ==================== hasTerminalPunctuation ====================

    @Nested
        @DisplayName("hasTerminalPunctuation 终止标点检测")
    class TerminalPunctuationTests {

        @ParameterizedTest
        @ValueSource(strings = {"。", "！", "？", "；", "!", "?", ".", ";"})
        @DisplayName("终止标点返回 true")
        void hasTerminalPunctuation_withPunctuation_returnsTrue(String token) {
            assertTrue(service.hasTerminalPunctuation(token));
        }

        @Test
        @DisplayName("包含终止标点的文本返回 true")
        void hasTerminalPunctuation_textWithPunctuation_returnsTrue() {
            assertTrue(service.hasTerminalPunctuation("你好。"));
        }

        @Test
        @DisplayName("无终止标点返回 false")
        void hasTerminalPunctuation_noPunctuation_returnsFalse() {
            assertFalse(service.hasTerminalPunctuation("你好"));
        }

        @Test
        @DisplayName("空字符串返回 false")
        void hasTerminalPunctuation_empty_returnsFalse() {
            assertFalse(service.hasTerminalPunctuation(""));
        }
    }
}
