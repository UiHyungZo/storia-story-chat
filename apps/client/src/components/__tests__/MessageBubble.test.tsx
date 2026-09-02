import { render, screen } from "@testing-library/react-native";
import { MessageBubble } from "../MessageBubble";
import { Message } from "../../types";

const base: Omit<Message, "role"> = {
  id: 1,
  content: "안녕하세요",
  createdAt: "2026-09-02T00:00:00.000Z",
};

describe("MessageBubble", () => {
  it("renders the message content", () => {
    render(<MessageBubble message={{ ...base, role: "assistant" }} />);
    expect(screen.getByText("안녕하세요")).toBeOnTheScreen();
  });

  it("styles a user message with the blue bubble and white text", () => {
    render(<MessageBubble message={{ ...base, role: "user", content: "user text" }} />);

    expect(screen.getByText("user text")).toHaveStyle({ color: "#FFFFFF" });
    expect(screen.getByTestId("message-bubble-user")).toHaveStyle({ backgroundColor: "#3B82F6" });
    expect(screen.queryByTestId("message-bubble-assistant")).toBeNull();
  });

  it("styles an assistant message with the grey bubble and dark text", () => {
    render(<MessageBubble message={{ ...base, role: "assistant", content: "reply text" }} />);

    expect(screen.getByText("reply text")).toHaveStyle({ color: "#111827" });
    expect(screen.getByTestId("message-bubble-assistant")).toHaveStyle({
      backgroundColor: "#E5E7EB",
    });
    expect(screen.queryByTestId("message-bubble-user")).toBeNull();
  });
});
