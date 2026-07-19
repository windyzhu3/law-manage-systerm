package com.ruoyi.system.foundation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class FoundationTestPasswordPolicyTest
{
    private final FoundationTestPasswordPolicy policy = new FoundationTestPasswordPolicy();

    @Test
    void rejectsMissingPasswordsWithTheRequiredCode()
    {
        assertCode(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PASSWORD_REQUIRED, null);
        assertCode(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PASSWORD_REQUIRED, "   ");
    }

    @Test
    void rejectsShortAndKnownWeakPasswordsWithoutLeakingThem()
    {
        for (String password : new String[] { "Short1!", "123456", "admin123", "password", "12345678", "  ADMIN123  " })
        {
            FoundationTestIdentityException exception = assertThrows(FoundationTestIdentityException.class,
                () -> policy.validate(password));
            assertEquals(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PASSWORD_WEAK, exception.getCode());
            assertFalse(exception.getMessage().contains(password.trim()));
        }
    }

    @Test
    void rejectsAPaddedShortPasswordUsingItsTrimmedLength()
    {
        String password = "  Abcdefghi  ";
        FoundationTestIdentityException exception = assertThrows(FoundationTestIdentityException.class,
            () -> policy.validate(password));
        assertEquals(FoundationTestIdentityErrorCode.FOUNDATION_TEST_IDENTITIES_PASSWORD_WEAK, exception.getCode());
        assertFalse(exception.getMessage().contains(password));
    }

    @Test
    void acceptsAStrongPasswordOfAtLeastTwelveCharacters()
    {
        assertDoesNotThrow(() -> policy.validate("Strong-test-passphrase-42!"));
    }

    private void assertCode(FoundationTestIdentityErrorCode expected, String password)
    {
        FoundationTestIdentityException exception = assertThrows(FoundationTestIdentityException.class,
            () -> policy.validate(password));
        assertEquals(expected, exception.getCode());
    }
}
