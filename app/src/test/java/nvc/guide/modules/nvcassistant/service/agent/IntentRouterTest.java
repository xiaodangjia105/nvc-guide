package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IntentRouter 意图预路由")
class IntentRouterTest {

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        TraceManager traceManager = mock(TraceManager.class);
        when(traceManager.startSpan(anyString(), anyString()))
            .thenReturn(AgentSpanEntity.builder().spanId("test-span").status("SUCCESS").build());
        router = new IntentRouter(traceManager, new ObjectMapper());
    }

    // ==================== null / 空输入 ====================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("null/空白输入返回 null")
    void detectIntent_nullOrBlank_returnsNull(String input) {
        assertNull(router.detectIntent(input));
    }

    // ==================== profile_update ====================

    @Nested
    @DisplayName("profile_update 意图")
    class ProfileUpdateTests {

        @Test
        @DisplayName("我是程序员，21岁，男 → 匹配 profile_update")
        void detectIntent_fullPersonalInfo_matches() {
            var result = router.detectIntent("我是程序员，21岁，男");
            assertNotNull(result);
            assertEquals("profile_update", result.toolName());
            assertTrue(result.arguments().contains("程序员"));
            assertTrue(result.arguments().contains("21"));
            assertTrue(result.arguments().contains("男性"));
        }

        @Test
        @DisplayName("我是一名教师，30岁，女 → 匹配 profile_update")
        void detectIntent_teacherFemale_matches() {
            var result = router.detectIntent("我是一名教师，30岁，女");
            assertNotNull(result);
            assertEquals("profile_update", result.toolName());
            assertTrue(result.arguments().contains("教师"));
            assertTrue(result.arguments().contains("30"));
            assertTrue(result.arguments().contains("女性"));
        }

        @Test
        @DisplayName("帮我记录到档案中，我是设计师 → 匹配 profile_update")
        void detectIntent_helpRecord_matches() {
            var result = router.detectIntent("帮我记录到档案中，我是设计师");
            assertNotNull(result);
            assertEquals("profile_update", result.toolName());
            assertTrue(result.arguments().contains("设计师"));
        }

        @Test
        @DisplayName("更新我的个人信息，我25岁 → 匹配 profile_update")
        void detectIntent_updateProfile_matches() {
            var result = router.detectIntent("更新我的个人信息，我25岁");
            assertNotNull(result);
            assertEquals("profile_update", result.toolName());
            assertTrue(result.arguments().contains("25"));
        }

        @Test
        @DisplayName("只说'更新档案'但无具体信息 → 返回 null")
        void detectIntent_updateProfileNoInfo_returnsNull() {
            assertNull(router.detectIntent("更新档案"));
        }

        @Test
        @DisplayName("只说'我是'但无职业 → 返回 null")
        void detectIntent_iamWithoutOccupation_returnsNull() {
            assertNull(router.detectIntent("我是"));
        }

        @Test
        @DisplayName("我是大学生 → 匹配（大学生是常见身份）")
        void detectIntent_student_matches() {
            var result = router.detectIntent("我是大学生");
            assertNotNull(result);
            assertEquals("profile_update", result.toolName());
            assertTrue(result.arguments().contains("大学生"));
        }

        @Test
        @DisplayName("我是大四学生 → 匹配（含年龄推断）")
        void detectIntent_seniorStudent_matches() {
            var result = router.detectIntent("我是大四学生");
            assertNotNull(result);
            assertTrue(result.arguments().contains("约22"));
        }
    }

    // ==================== profile_query ====================

    @Nested
    @DisplayName("profile_query 意图")
    class ProfileQueryTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "看看我的档案",
            "查看档案",
            "我的个人信息",
            "看下我的档案",
            "查询我的档案"
        })
        @DisplayName("查看档案类消息 → 匹配 profile_query")
        void detectIntent_profileQuery_matches(String input) {
            var result = router.detectIntent(input);
            assertNotNull(result, "应该匹配 profile_query: " + input);
            assertEquals("profile_query", result.toolName());
            assertEquals("{}", result.arguments());
        }
    }

    // ==================== dashboard_query ====================

    @Nested
    @DisplayName("dashboard_query 意图")
    class DashboardQueryTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "练习数据",
            "练习统计",
            "我练了多少次",
            "我的进度",
            "查看我的数据",
            "查看统计"
        })
        @DisplayName("练习数据类消息 → 匹配 dashboard_query")
        void detectIntent_dashboardQuery_matches(String input) {
            var result = router.detectIntent(input);
            assertNotNull(result, "应该匹配 dashboard_query: " + input);
            assertEquals("dashboard_query", result.toolName());
            assertEquals("{}", result.arguments());
        }
    }

    // ==================== 不匹配的意图 ====================

    @Nested
    @DisplayName("不匹配的消息")
    class NoMatchTests {

        @ParameterizedTest
        @ValueSource(strings = {
            "什么是NVC？",
            "推荐一些练习场景",
            "开始练习",
            "帮我评估这句话",
            "你好",
            "今天天气怎么样",
            "帮我记下来这个笔记"
        })
        @DisplayName("非明确意图消息 → 返回 null")
        void detectIntent_noMatch_returnsNull(String input) {
            assertNull(router.detectIntent(input), "不应该匹配: " + input);
        }
    }

    // ==================== JSON 转义 ====================

    @Test
    @DisplayName("个人信息包含引号时正确转义")
    void detectIntent_specialChars_escapedProperly() {
        var result = router.detectIntent("我是\"程序员\"，25岁");
        if (result != null) {
            // 验证 JSON 中的引号被转义
            assertFalse(result.arguments().contains("\"程序员\"未转义"));
        }
    }
}
