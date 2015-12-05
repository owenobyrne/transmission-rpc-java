package nl.stil4m.transmission.rpc;

import nl.stil4m.transmission.http.HostConfiguration;

import java.net.URI;

public class RpcConfiguration implements HostConfiguration {

    private URI host;

    private String username;
    private String password;

    public URI getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setHost(URI host) {
        this.host = host;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
