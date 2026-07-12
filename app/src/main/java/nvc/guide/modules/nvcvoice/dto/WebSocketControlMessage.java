package nvc.guide.modules.nvcvoice.dto;

/**
 * WebSocket 控制消息
 */
public record WebSocketControlMessage(
    String type,
    String action,
    String data
) {

  public static WebSocketControlMessage of(String action) {
    return new WebSocketControlMessage("control", action, null);
  }

  public static WebSocketControlMessage of(String action, String data) {
    return new WebSocketControlMessage("control", action, data);
  }
}
