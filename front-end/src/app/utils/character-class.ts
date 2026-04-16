function normalizeClassName(value: string): string {
  return value.trim().toLowerCase().replace(/[_\s-]+/g, ' ');
}

function toTitleCase(value: string): string {
  return value
    .split(/\s+/)
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

export function isWhisperbinderClass(value: string): boolean {
  const normalized = normalizeClassName(value);
  return normalized === 'mage' || normalized === 'whisperbinder';
}

export function formatCharacterClass(value: string): string {
  if (isWhisperbinderClass(value)) {
    return 'Whisperbinder';
  }

  return toTitleCase(value.replace(/[_-]+/g, ' '));
}

export function classProgressionNoun(value: string): string {
  return isWhisperbinderClass(value) ? 'utterances' : 'arts';
}