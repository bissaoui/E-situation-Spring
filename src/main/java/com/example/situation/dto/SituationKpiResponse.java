package com.example.situation.dto;

import java.util.List;

public record SituationKpiResponse(
    String scope,
    long total,
    long payed,
    long rejected,
    long canceled,
    long other,
    List<KpiSlice> chart
) {}
