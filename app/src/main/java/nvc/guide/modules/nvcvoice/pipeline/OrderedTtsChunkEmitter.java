package nvc.guide.modules.nvcvoice.pipeline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * TTS 分片排序发射器
 *
 * 管理并发 TTS 合成任务，确保音频分片按顺序发送。
 * 使用 CompletableFuture 收集乱序的 TTS 结果，按序号顺序排放。
 */
@Slf4j
public class OrderedTtsChunkEmitter {

  private final ConcurrentHashMap<Integer, CompletableFuture<byte[]>> chunks =
      new ConcurrentHashMap<>();
  private final AtomicInteger nextSeq = new AtomicInteger(0);
  private final AtomicInteger submittedSeq = new AtomicInteger(0);
  private final Consumer<byte[]> onChunk;
  private final Consumer<byte[]> onComplete;
  private final ExecutorService drainExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "tts-chunk-drain");
    t.setDaemon(true);
    return t;
  });

  /**
   * 创建发射器
   *
   * @param onChunk     每个分片就绪时的回调
   * @param onComplete  所有分片发送完毕后的回调（传入合并后的完整音频）
   */
  public OrderedTtsChunkEmitter(Consumer<byte[]> onChunk, Consumer<byte[]> onComplete) {
    this.onChunk = onChunk;
    this.onComplete = onComplete;
  }

  /**
   * 提交一个 TTS 任务
   *
   * @param ttsTask TTS 合成任务（返回 PCM 音频）
   */
  public synchronized void submit(CompletableFuture<byte[]> ttsTask) {
    int seq = submittedSeq.getAndIncrement();
    chunks.put(seq, ttsTask);

    ttsTask.whenComplete((audio, error) -> {
      if (error != null) {
        log.warn("[TtsEmitter] TTS task {} failed: {}", seq, error.getMessage());
        chunks.remove(seq);
      }
      drain();
    });
  }

  /**
   * 标记所有任务已提交，触发最终合并
   */
  public void markComplete() {
    drainExecutor.submit(() -> {
      // 等待所有分片完成
      while (!chunks.isEmpty()) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }

      // 合并所有音频
      if (onComplete != null) {
        onComplete.accept(new byte[0]);
      }
    });
  }

  /**
   * 按序排放已完成的分片
   */
  private void drain() {
    drainExecutor.submit(() -> {
      while (true) {
        CompletableFuture<byte[]> future = chunks.get(nextSeq.get());
        if (future == null || !future.isDone()) {
          break;
        }

        try {
          byte[] audio = future.get();
          chunks.remove(nextSeq.getAndIncrement());

          if (audio != null && audio.length > 0 && onChunk != null) {
            onChunk.accept(audio);
          }
        } catch (Exception e) {
          log.warn("[TtsEmitter] Failed to get chunk {}: {}", nextSeq.get(), e.getMessage());
          chunks.remove(nextSeq.getAndIncrement());
        }
      }
    });
  }

  /**
   * 关闭发射器
   */
  public void shutdown() {
    drainExecutor.shutdown();
  }
}
