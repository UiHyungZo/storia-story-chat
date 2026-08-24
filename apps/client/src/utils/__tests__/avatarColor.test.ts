import { avatarColorFor } from "../avatarColor";

describe("avatarColorFor", () => {
  it("returns a deterministic color for the same id", () => {
    expect(avatarColorFor(1)).toBe(avatarColorFor(1));
  });

  it("wraps around the palette for ids beyond its length", () => {
    const paletteSize = 6;
    expect(avatarColorFor(0)).toBe(avatarColorFor(paletteSize));
  });

  it("returns a hex color string", () => {
    expect(avatarColorFor(3)).toMatch(/^#[0-9A-Fa-f]{6}$/);
  });
});
