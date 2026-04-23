package com.devtools.ui.core.requests;

import com.devtools.ui.core.model.CapturedRequest;

import java.util.List;

public interface RequestCapturePersistence {

    List<CapturedRequest> load();

    void save(List<CapturedRequest> requests);
}
