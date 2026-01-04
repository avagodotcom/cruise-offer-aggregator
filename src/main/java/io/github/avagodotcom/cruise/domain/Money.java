package io.github.avagodotcom.cruise.domain;

public record Money(long cents, String currency) {
    public String format() {
        return String.format("$%.2f %s", cents / 100.0, currency);
    }
}
