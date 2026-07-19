package com.ruoyi.system.foundation;

import java.util.Locale;
import java.util.Set;

public class FoundationTestPasswordPolicy
{
    private static final Set<String> WEAK_PASSWORDS = Set.of("123456", "admin123", "password", "12345678");

    public void validate(String password)
    {
        if (password == null || password.isBlank())
        {
            throw new FoundationTestIdentityException(
                FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PASSWORD_REQUIRED, "credential");
        }

        String normalizedPassword = password.trim().toLowerCase(Locale.ROOT);
        if (password.length() < 12 || WEAK_PASSWORDS.contains(normalizedPassword))
        {
            throw new FoundationTestIdentityException(
                FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PASSWORD_WEAK, "credential");
        }
    }
}
