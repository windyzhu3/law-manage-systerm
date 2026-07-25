package com.ruoyi.web.e2e;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="foundation.e2e-identity")
public class E2eBackendIdentityProperties
{
    private boolean enabled;
    private String secret;

    public boolean isEnabled(){return enabled;}
    public void setEnabled(boolean enabled){this.enabled=enabled;}
    public String getSecret(){return secret;}
    public void setSecret(String secret){this.secret=secret;}
}
