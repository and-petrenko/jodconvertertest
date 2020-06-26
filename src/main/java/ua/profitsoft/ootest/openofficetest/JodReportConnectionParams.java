package ua.profitsoft.ootest.openofficetest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Поименованные параметры подключения к Open/Libre Office
 */
@Component
public class JodReportConnectionParams {

    @Value("${jodConverter.host}")
    private String host;

    @Value("${jodConverter.port}")
    private int port;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
