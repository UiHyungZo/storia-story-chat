import { useConversationStore } from "../useConversationStore";
import { fetchMessages, postMessage } from "../../api/conversations";
import {
  connectConversationSocket,
  disconnectConversationSocket,
  isSocketConnected,
  sendConversationMessage,
} from "../../api/websocket";
import { readCache, writeCache } from "../../storage/cache";
import { notifyNewMessage } from "../../../modules/storia-native";
import { Message } from "../../types";

jest.mock("../../api/conversations", () => ({
  fetchMessages: jest.fn(),
  postMessage: jest.fn(),
}));
jest.mock("../../api/websocket", () => ({
  connectConversationSocket: jest.fn(),
  disconnectConversationSocket: jest.fn(),
  isSocketConnected: jest.fn(),
  sendConversationMessage: jest.fn(),
}));
jest.mock("../../storage/cache", () => ({
  readCache: jest.fn(),
  writeCache: jest.fn(),
  messagesCacheKey: (id: number) => `messages:${id}`,
}));
jest.mock("../../../modules/storia-native", () => ({ notifyNewMessage: jest.fn() }));
jest.mock("../useCharacterStore", () => ({
  useCharacterStore: { getState: () => ({ getCharacterById: () => ({ name: "루나" }) }) },
}));

const mockedFetchMessages = fetchMessages as jest.MockedFunction<typeof fetchMessages>;
const mockedPostMessage = postMessage as jest.MockedFunction<typeof postMessage>;
const mockedConnect = connectConversationSocket as jest.MockedFunction<typeof connectConversationSocket>;
const mockedDisconnect = disconnectConversationSocket as jest.MockedFunction<typeof disconnectConversationSocket>;
const mockedIsSocketConnected = isSocketConnected as jest.MockedFunction<typeof isSocketConnected>;
const mockedSendConversationMessage = sendConversationMessage as jest.MockedFunction<typeof sendConversationMessage>;
const mockedNotifyNewMessage = notifyNewMessage as jest.MockedFunction<typeof notifyNewMessage>;

const CHARACTER_ID = 1;

const userMessage: Message = { id: 10, role: "user", content: "안녕", createdAt: "2026-01-01T00:00:00Z" };
const assistantMessage: Message = { id: 11, role: "assistant", content: "안녕하세요", createdAt: "2026-01-01T00:00:01Z" };

describe("useConversationStore", () => {
  const initialState = useConversationStore.getState();

  beforeEach(() => {
    useConversationStore.setState(initialState, true);
    jest.clearAllMocks();
    (readCache as jest.MockedFunction<typeof readCache>).mockResolvedValue(null);
  });

  it("resets transport on disconnect so the next screen visit re-tries WS", () => {
    useConversationStore.setState({ transportByCharacterId: { [CHARACTER_ID]: "rest" } });

    useConversationStore.getState().disconnect(CHARACTER_ID);

    expect(mockedDisconnect).toHaveBeenCalledWith(CHARACTER_ID);
    expect(useConversationStore.getState().transportByCharacterId[CHARACTER_ID]).toBeUndefined();
  });

  it("falls back to REST for the whole session when the WS connect attempt throws", async () => {
    mockedConnect.mockRejectedValue(new Error("connect failed"));
    mockedPostMessage.mockResolvedValue({ userMessage, assistantMessage });

    await useConversationStore.getState().sendMessage(CHARACTER_ID, "안녕");

    expect(useConversationStore.getState().transportByCharacterId[CHARACTER_ID]).toBe("rest");
    expect(mockedPostMessage).toHaveBeenCalledWith(CHARACTER_ID, "안녕");
    expect(useConversationStore.getState().messagesByCharacterId[CHARACTER_ID]).toEqual([
      userMessage,
      assistantMessage,
    ]);
    expect(mockedNotifyNewMessage).toHaveBeenCalledWith("루나", assistantMessage.content);
  });

  it("does not duplicate the optimistic user message when a WS publish fails mid-send", async () => {
    // WS connects fine, so transport is "ws" and the socket reports connected...
    mockedConnect.mockResolvedValue(undefined);
    mockedIsSocketConnected.mockReturnValue(true);
    // ...but the actual publish fails (socket dropped between the check and the send).
    mockedSendConversationMessage.mockResolvedValue(false);
    mockedPostMessage.mockResolvedValue({ userMessage, assistantMessage });

    await useConversationStore.getState().sendMessage(CHARACTER_ID, "안녕");

    const messages = useConversationStore.getState().messagesByCharacterId[CHARACTER_ID];
    // Only the REST response's copies should remain — the locally-added optimistic
    // bubble (negative id) must have been removed before the REST call ran.
    expect(messages).toEqual([userMessage, assistantMessage]);
    expect(messages?.filter((m) => m.id < 0)).toHaveLength(0);
  });

  it("stays on WS and skips REST entirely when the publish succeeds", async () => {
    mockedConnect.mockResolvedValue(undefined);
    mockedIsSocketConnected.mockReturnValue(true);
    mockedSendConversationMessage.mockResolvedValue(true);

    await useConversationStore.getState().sendMessage(CHARACTER_ID, "안녕");

    expect(mockedPostMessage).not.toHaveBeenCalled();
    const messages = useConversationStore.getState().messagesByCharacterId[CHARACTER_ID];
    expect(messages).toHaveLength(1);
    expect(messages?.[0].content).toBe("안녕");
  });

  it("re-checks live socket state per send instead of trusting a cached ws transport", async () => {
    // A previous send already resolved transport to "ws"...
    useConversationStore.setState({ transportByCharacterId: { [CHARACTER_ID]: "ws" } });
    // ...but the socket is currently down (reconnecting) — this send should fall back to REST.
    mockedIsSocketConnected.mockReturnValue(false);
    mockedPostMessage.mockResolvedValue({ userMessage, assistantMessage });

    await useConversationStore.getState().sendMessage(CHARACTER_ID, "안녕");

    expect(mockedConnect).not.toHaveBeenCalled();
    expect(mockedSendConversationMessage).not.toHaveBeenCalled();
    expect(mockedPostMessage).toHaveBeenCalledWith(CHARACTER_ID, "안녕");
  });

  it("sendMessageViaRest always uses REST and returns the assistant message, regardless of transport", async () => {
    useConversationStore.setState({ transportByCharacterId: { [CHARACTER_ID]: "ws" } });
    mockedPostMessage.mockResolvedValue({ userMessage, assistantMessage });

    const result = await useConversationStore.getState().sendMessageViaRest(CHARACTER_ID, "안녕");

    expect(result).toEqual(assistantMessage);
    expect(mockedConnect).not.toHaveBeenCalled();
    expect(mockedSendConversationMessage).not.toHaveBeenCalled();
  });

  it("loadMessages surfaces fetch errors without throwing", async () => {
    mockedFetchMessages.mockRejectedValue(new Error("network down"));

    await useConversationStore.getState().loadMessages(CHARACTER_ID);

    expect(useConversationStore.getState().error).toBe("network down");
    expect(useConversationStore.getState().isLoading).toBe(false);
  });
});
