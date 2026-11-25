package com.quizmaster.taxonomy;

import java.util.List;

public record TaxonomyPage<T>(List<T> items, int page, int size, long total) {}
