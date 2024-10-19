package com.dazo66.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;

@Slf4j
public class IpUtils {

    @SneakyThrows
    public static String getLocalIpv6() {
        Enumeration<NetworkInterface> interfs = NetworkInterface.getNetworkInterfaces();
        while (interfs.hasMoreElements()) {
            NetworkInterface interf = interfs.nextElement();
            Enumeration<InetAddress> addres = interf.getInetAddresses();
            while (addres.hasMoreElements()) {
                InetAddress in = addres.nextElement();
                if (in instanceof Inet6Address && in.isReachable(100) && in.getHostAddress().startsWith("2")) {
                    return in.getHostAddress();
                }
            }
        }
        return null;
    }

    @SneakyThrows
    public static String getLocalIpv4() {
        return HttpUtils.doGet("http://ipinfo.io", "/ip", Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * 检测Ip和端口是否可用
     *
     * @param ip
     * @param port
     * @return
     */
    public static boolean checkIpPort(String ip, int port) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, port), 3000);
            log.info("地址和端口号可用");
            return true;
        } catch (Exception e) {
            log.info("地址和端口号不可用");
            return false;
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }

    }

    /**
     * 检测Ip地址
     *
     * @param ip
     * @return
     */
    public static boolean checkIp(String ip) {
        try {
            InetAddress.getByName(ip).isReachable(3000);
            log.info("Ip可以使用");
            return true;
        } catch (IOException e) {
            log.info("Ip不可用");
            return false;
        }
    }

}
