package com.gameflix;

import com.gameflix.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Password rules on registration: 8-64 characters with lowercase, uppercase, number and symbol. */
class PasswordPolicyTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private List<String> messagesFor(String password) {
        return validator.validate(new RegisterRequest("fan@example.com", password, "Fan")).stream()
                .map(ConstraintViolation::getMessage)
                .toList();
    }

    @Test
    void strongPasswordIsAccepted() {
        assertTrue(messagesFor("Throwaway#Pass1").isEmpty());
    }

    @Test
    void passwordWithoutLowercaseIsRejected() {
        assertEquals(List.of("password must contain a lowercase letter"), messagesFor("THROWAWAY#PASS1"));
    }

    @Test
    void passwordWithoutUppercaseIsRejected() {
        assertEquals(List.of("password must contain an uppercase letter"), messagesFor("throwaway#pass1"));
    }

    @Test
    void passwordWithoutNumberIsRejected() {
        assertEquals(List.of("password must contain a number"), messagesFor("Throwaway#Pass"));
    }

    @Test
    void passwordWithoutSymbolIsRejected() {
        assertEquals(List.of("password must contain a symbol"), messagesFor("Throwaway1Pass"));
    }

    @Test
    void shortPasswordIsRejected() {
        assertTrue(messagesFor("Aa1!").contains("password must be 8 to 64 characters"));
    }

    @Test
    void passwordOver64CharactersIsRejected() {
        String tooLong = "Aa1!" + "x".repeat(61);
        assertTrue(messagesFor(tooLong).contains("password must be 8 to 64 characters"));
    }

    @Test
    void weakPasswordReportsEveryMissingRule() {
        List<String> messages = messagesFor("abcdefgh");
        assertEquals(3, messages.size());
        assertTrue(messages.contains("password must contain an uppercase letter"));
        assertTrue(messages.contains("password must contain a number"));
        assertTrue(messages.contains("password must contain a symbol"));
    }
}
