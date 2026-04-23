package com.devtools.ui.autoconfigure;

class NoOpTunnelStreamClient implements TunnelStreamClient {

    @Override
    public TunnelStreamHandle connect(String streamUrl, TunnelEventListener listener) {
        listener.onConnected();
        listener.onEvent("noop", "local-stub");
        return () -> listener.onClosed();
    }
}
