package com.ruoyi.web.e2e;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.springframework.security.access.AccessDeniedException;

public class E2eBackendIdentityService
{
    private final DataSource dataSource;
    private final E2eBackendIdentityProperties properties;
    private final String buildVersion;

    public E2eBackendIdentityService(DataSource dataSource,E2eBackendIdentityProperties properties,
        String buildVersion)
    {
        this.dataSource=dataSource;
        this.properties=properties;
        this.buildVersion=buildVersion;
    }

    public BackendIdentity identity(String suppliedSecret,String nonce,String marker)
    {
        requireText(nonce,"run nonce");
        requireText(marker,"fixture marker");
        if(properties.getSecret()==null||properties.getSecret().length()<32
            ||suppliedSecret==null||!MessageDigest.isEqual(
                properties.getSecret().getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("E2E backend identity secret is invalid");
        try(Connection connection=dataSource.getConnection();
            Statement statement=connection.createStatement();
            ResultSet resultSet=statement.executeQuery("select database()"))
        {
            if(!resultSet.next())throw new IllegalStateException("JDBC database identity is unavailable");
            String schema=resultSet.getString(1);
            String catalog=connection.getCatalog();
            if(catalog==null||catalog.isBlank())catalog=schema;
            BackendIdentity unsigned=new BackendIdentity(catalog,schema,nonce,marker,buildVersion,null);
            return new BackendIdentity(catalog,schema,nonce,marker,buildVersion,
                proof(properties.getSecret(),unsigned));
        }
        catch(java.sql.SQLException error)
        {
            throw new IllegalStateException("Unable to read the JDBC database identity",error);
        }
    }

    static String proof(String secret,BackendIdentity identity)
    {
        try
        {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(identity).getBytes(StandardCharsets.UTF_8)));
        }
        catch(java.security.GeneralSecurityException error)
        {
            throw new IllegalStateException("Unable to sign E2E backend identity",error);
        }
    }

    private static String canonical(BackendIdentity identity)
    {
        return String.join("\n",safe(identity.catalog()),safe(identity.schema()),safe(identity.nonce()),
            safe(identity.marker()),safe(identity.buildVersion()));
    }

    private static String safe(String value){return value==null?"":value;}
    private static void requireText(String value,String label)
    {if(value==null||value.isBlank()||value.length()>128)throw new IllegalArgumentException(label+" is invalid");}

    public record BackendIdentity(String catalog,String schema,String nonce,String marker,
        String buildVersion,String proof){}
}
