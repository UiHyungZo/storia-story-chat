const PALETTE = ["#3B82F6", "#92400E", "#57534E", "#7C3AED", "#059669", "#DB2777"];

export function avatarColorFor(id: number): string {
  return PALETTE[id % PALETTE.length];
}
