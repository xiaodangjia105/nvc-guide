package nvc.guide.modules.nvcvoice.dto;

/**
 * WebSocket 字幕消息
 */
public record WebSocketSubtitleMessage(
    String type,
    String text,
    String source,
    boolean partial
) {

  public static WebSocketSubtitleMessage userPartial(String text) {
    return new WebSocketSubtitleMessage("subtitle", text, "user", true);
  }

  public static WebSocketSubtitleMessage userFinal(String text) {
    return new WebSocketSubtitleMessage("subtitle", text, "user", false);
  }

  public static WebSocketSubtitleMessage aiPartial(String text) {
    return new WebSocketSubtitleMessage("subtitle", text, "ai", true);
  }

  public static WebSocketSubtitleMessage aiFinal(String text) {
    return new WebSocketSubtitleMessage("subtitle", text, "ai", false);
  }
}
