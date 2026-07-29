package nvc.guide.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * 替代 Spring 默认的 SimpleAsyncTaskExecutor（无界线程池，OOM 风险）
 *
 * 实现 AsyncConfigurer 提供默认线程池：
 * - 未指定 bean 名称的 @Async 方法也会使用此线程池（而非 SimpleAsyncTaskExecutor）
 * - 已指定 bean 名称的 @Async("asyncExecutor") 同样生效
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("nvc-async-");
        // 队列满时由调用线程执行，避免 RejectedExecutionException
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
