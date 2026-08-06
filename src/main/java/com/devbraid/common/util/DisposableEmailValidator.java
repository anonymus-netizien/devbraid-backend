package com.devbraid.common.util;

import java.util.Set;

public final class DisposableEmailValidator {

    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com", "guerrillamail.com", "10minutemail.com", "tempmail.com",
            "throwaway.email", "yopmail.com", "sharklasers.com", "trashmail.com",
            "getnada.com", "maildrop.cc", "temp-mail.org", "fakeinbox.com",
            "mailnesia.com", "inboxes.com", "mailmetrash.com", "spambox.com",
            "dispostable.com", "tempinbox.com", "mytemp.email", "mailcatch.com",
            "emailondeck.com", "tempemail.net", "burnermail.io", "moakt.com",
            "tmpmail.org", "temp-mailbox.com", "mail-temp.com"
    );

    private DisposableEmailValidator() {
    }

    public static boolean isDisposable(String email) {
        if (email == null || !email.contains("@")) return false;
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase().trim();
        return DISPOSABLE_DOMAINS.contains(domain);
    }
}
