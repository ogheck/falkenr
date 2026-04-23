package com.devtools.ui.core.collector;

import java.util.List;

public interface DevToolsCollector<T> {

    String id();

    List<T> collect();
}
