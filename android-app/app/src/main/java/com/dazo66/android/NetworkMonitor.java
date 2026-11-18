package com.dazo66.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.util.ArrayList;
import java.util.Collections;

/** Reset a remembered fallback only when the actual network environment changes. */
final class NetworkMonitor extends ConnectivityManager.NetworkCallback {
    private final Runnable changed;
    private Network current;
    private String transports, links;
    NetworkMonitor(Context context, Runnable changed) {
        this.changed = changed;
        context.getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(this);
    }
    @Override public synchronized void onAvailable(Network network) {
        if (!network.equals(current)) { current = network; transports = null; links = null; changed.run(); }
    }
    @Override public synchronized void onLost(Network network) {
        if (network.equals(current)) { current = null; transports = null; links = null; changed.run(); }
    }
    @Override public synchronized void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        if (!network.equals(current)) return;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i <= 10; i++) if (capabilities.hasTransport(i)) value.append(i).append(',');
        String next = value.toString();
        if (transports != null && !transports.equals(next)) changed.run();
        transports = next;
    }
    @Override public synchronized void onLinkPropertiesChanged(Network network, LinkProperties properties) {
        if (!network.equals(current)) return;
        ArrayList<String> values = new ArrayList<>();
        properties.getLinkAddresses().forEach(address -> values.add(address.toString()));
        properties.getDnsServers().forEach(address -> values.add(address.getHostAddress()));
        Collections.sort(values);
        String next = properties.getInterfaceName() + ":" + values + ":" + properties.getHttpProxy();
        if (links != null && !links.equals(next)) changed.run();
        links = next;
    }
}
