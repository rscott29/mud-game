package com.scott.tech.mud.mud_game.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class Shop {

    public record Listing(String itemId, Item item, int price) {
        public Listing {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId is required");
            }
            if (item == null) {
                throw new IllegalArgumentException("item is required");
            }
            price = Math.max(0, price);
        }

        boolean matchesExactly(String input) {
            String normalized = normalize(input);
            if (normalized.isEmpty()) {
                return false;
            }

            return normalize(itemId).equals(normalized)
                    || item.hasExactKeyword(input)
                    || normalize(item.getName()).equals(normalized);
        }

        boolean matchesLoosely(String input) {
            String normalized = normalize(input);
            if (normalized.isEmpty()) {
                return false;
            }
            return normalize(itemId).equals(normalized) || item.matchesKeyword(input);
        }

        private static String normalize(String value) {
            if (value == null) {
                return "";
            }
            return value.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9\\s]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }
    }

    private final String merchantNpcId;
    private final List<Listing> listings;

    public Shop(String merchantNpcId, List<Listing> listings) {
        this.merchantNpcId = merchantNpcId;
        this.listings = List.copyOf(listings != null ? listings : List.of());
    }

    public String getMerchantNpcId() {
        return merchantNpcId;
    }

    public List<Listing> getListings() {
        return listings;
    }

    public boolean isEmpty() {
        return listings.isEmpty();
    }

    public Optional<Listing> findListing(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        return listings.stream()
                .filter(listing -> listing.matchesExactly(input))
                .findFirst()
                .or(() -> listings.stream().filter(listing -> listing.matchesLoosely(input)).findFirst());
    }
}