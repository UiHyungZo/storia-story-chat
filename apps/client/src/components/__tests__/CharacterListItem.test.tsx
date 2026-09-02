import { render, screen, fireEvent } from "@testing-library/react-native";
import { CharacterListItem } from "../CharacterListItem";
import { Character } from "../../types";

const character: Character = {
  id: 3,
  name: "렌",
  concept: "낡은 서점의 시간을 파는 주인",
  ttsVoiceId: null,
};

describe("CharacterListItem", () => {
  it("renders the character name and concept", () => {
    render(<CharacterListItem character={character} onPress={jest.fn()} />);

    expect(screen.getByText("렌")).toBeOnTheScreen();
    expect(screen.getByText("낡은 서점의 시간을 파는 주인")).toBeOnTheScreen();
  });

  it("calls onPress when tapped", () => {
    const onPress = jest.fn();
    render(<CharacterListItem character={character} onPress={onPress} />);

    fireEvent.press(screen.getByText("렌"));

    expect(onPress).toHaveBeenCalledTimes(1);
  });
});
