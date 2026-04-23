package com.devtools.ui.autoconfigure;

interface TunnelStreamClient {

    TunnelStreamHandle connect(String streamUrl, TunnelEventListener listener);

    interface TunnelStreamHandle {
        void close();
    }

    interface TunnelEventListener {
        void onConnected();

        void onEvent(String eventName, String payload);

        void onClosed();

        void onError(String message);
    }
}
