package nvc.guide.modules.nvcassistant.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TraceSampler 采样率控制")
class TraceSamplerTest {

    private TraceProperties traceProperties;
    private TraceSampler sampler;

    @BeforeEach
    void setUp() {
        traceProperties = new TraceProperties();
        sampler = new TraceSampler(traceProperties);
    }

    @Nested
    @DisplayName("shouldSample - 采样决策")
    class ShouldSample {

        @Test
        @DisplayName("采样率 100% 时应该总是采样")
        void shouldAlwaysSampleWhenRateIs100() {
            traceProperties.getSampling().setEnabled(true);
            traceProperties.getSampling().setRate(1.0);

            // 执行多次采样
            for (int i = 0; i < 100; i++) {
                assertTrue(sampler.shouldSample("user-" + i, "session-" + i));
            }
        }

        @Test
        @DisplayName("采样率 0% 时应该总是跳过")
        void shouldNeverSampleWhenRateIs0() {
            traceProperties.getSampling().setEnabled(true);
            traceProperties.getSampling().setRate(0.0);

            // 执行多次采样
            for (int i = 0; i < 100; i++) {
                assertFalse(sampler.shouldSample("user-" + i, "session-" + i));
            }
        }

        @Test
        @DisplayName("采样率 50% 时应该大约采样一半")
        void shouldSampleAboutHalfWhenRateIs50() {
            traceProperties.getSampling().setEnabled(true);
            traceProperties.getSampling().setRate(0.5);

            int sampledCount = 0;
            int totalRuns = 1000;

            for (int i = 0; i < totalRuns; i++) {
                if (sampler.shouldSample("user-" + i, "session-" + i)) {
                    sampledCount++;
                }
            }

            // 允许 10% 的误差
            double actualRate = (double) sampledCount / totalRuns;
            assertTrue(actualRate > 0.4 && actualRate < 0.6,
                "Expected rate around 0.5, got: " + actualRate);
        }

        @Test
        @DisplayName("禁用采样时应该总是采样")
        void shouldAlwaysSampleWhenDisabled() {
            traceProperties.getSampling().setEnabled(false);

            // 执行多次采样
            for (int i = 0; i < 100; i++) {
                assertTrue(sampler.shouldSample("user-" + i, "session-" + i));
            }
        }

        @Test
        @DisplayName("调试用户应该总是采样")
        void shouldAlwaysSampleDebugUsers() {
            traceProperties.getSampling().setEnabled(true);
            traceProperties.getSampling().setRate(0.0); // 0% 采样率
            traceProperties.getRuntime().setDebugUsers(java.util.List.of(1001L, 1002L));

            // 调试用户应该总是采样
            assertTrue(sampler.shouldSample("1001", "session-1"));
            assertTrue(sampler.shouldSample("1002", "session-2"));

            // 非调试用户应该不采样
            assertFalse(sampler.shouldSample("9999", "session-3"));
        }

        @Test
        @DisplayName("调试会话应该总是采样")
        void shouldAlwaysSampleDebugSessions() {
            traceProperties.getSampling().setEnabled(true);
            traceProperties.getSampling().setRate(0.0); // 0% 采样率
            traceProperties.getRuntime().setDebugSessions(java.util.List.of(100L, 200L));

            // 调试会话应该总是采样
            assertTrue(sampler.shouldSample("user-1", "100"));
            assertTrue(sampler.shouldSample("user-2", "200"));

            // 非调试会话应该不采样
            assertFalse(sampler.shouldSample("user-3", "999"));
        }
    }

    @Nested
    @DisplayName("getSampledRate - 获取实际采样率")
    class GetSampledRate {

        @Test
        @DisplayName("应该返回配置的采样率")
        void shouldReturnConfiguredRate() {
            traceProperties.getSampling().setEnabled(true);
            traceProperties.getSampling().setRate(0.3);

            assertEquals(0.3, sampler.getSampledRate());
        }

        @Test
        @DisplayName("禁用时应该返回 1.0")
        void shouldReturn1WhenDisabled() {
            traceProperties.getSampling().setEnabled(false);

            assertEquals(1.0, sampler.getSampledRate());
        }
    }
}
