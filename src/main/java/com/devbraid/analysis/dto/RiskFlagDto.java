package com.devbraid.analysis.dto;

import com.devbraid.changethread.entity.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskFlagDto {

    private String rule;
    private RiskLevel severity;
    private String message;
    private List<String> evidence;
}
