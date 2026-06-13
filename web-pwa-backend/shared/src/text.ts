const viLocale = "vi-VN";

export function searchable(text: string): string {
  return text
    .toLocaleLowerCase(viLocale)
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .replace(/đ/g, "d");
}

export function containsAny(text: string, keywords: string[]): boolean {
  return keywords.some((keyword) => text.includes(keyword.toLocaleLowerCase(viLocale)));
}

export function compactWhitespace(text: string): string {
  return text.replace(/\s+/g, " ").trim();
}
