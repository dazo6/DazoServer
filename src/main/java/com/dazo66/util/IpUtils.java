package com.dazo66.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

@Slf4j
public class IpUtils {

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
