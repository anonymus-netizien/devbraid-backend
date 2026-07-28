package com.devbraid.changethread.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNoteRequest {

    private String decision;

    private String rationale;

    private String alternatives;

    private String impact;
}
