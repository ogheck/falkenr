package com.devtools.ui.autoconfigure;

record RemoteSessionAttachRequest(
        String ownerName,
        boolean allowGuests
) {
}
