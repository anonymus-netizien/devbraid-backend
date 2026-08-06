package com.devbraid.github.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangedFileDto {
    private String filename;
    private String status;
    private int additions;
    private int deletions;
    private String patch;

    // Backwards-compatible 4-arg constructor for existing callers/tests
    public ChangedFileDto(String filename, String status, int additions, int deletions) {
        this(filename, status, additions, deletions, null);
    }
}
