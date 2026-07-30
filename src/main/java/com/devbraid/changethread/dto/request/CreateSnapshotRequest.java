package com.devbraid.changethread.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSnapshotRequest {

    /**
     * Optional note to annotate the snapshot.
     */
    private String note;
}
