package com.devtools.ui.core.db;

import com.devtools.ui.core.collector.DevToolsCollector;
import com.devtools.ui.core.model.DbQueryDescriptor;

import java.util.List;

public class DbQueryCollector implements DevToolsCollector<DbQueryDescriptor> {

    private final DbQueryCaptureStore store;

    public DbQueryCollector(DbQueryCaptureStore store) {
        this.store = store;
    }

    @Override
    public String id() {
        return "dbQueries";
    }

    @Override
    public List<DbQueryDescriptor> collect() {
        return store.snapshot();
    }
}
