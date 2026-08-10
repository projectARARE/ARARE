package com.arare.features.dataimport;

import java.util.List;

/**
 * Describes one step of the dependency-ordered import sequence, used by the
 * {@code GET /import/order} endpoint so UIs can present the canonical order.
 */
public record ImportOrderStep(
    String name,
    String displayName,
    String fileName,
    List<String> dependencies
) {
}