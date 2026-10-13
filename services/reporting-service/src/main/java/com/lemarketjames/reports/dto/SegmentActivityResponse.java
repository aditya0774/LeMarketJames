package com.lemarketjames.reports.dto;

import java.util.List;

public record SegmentActivityResponse(boolean success, List<SegmentActivityRow> data) {}
