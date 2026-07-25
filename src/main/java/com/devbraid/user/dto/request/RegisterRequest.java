package com.devbraid.user.dto.request;

import com.devbraid.common.util.DisposableEmailValidator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 80, message = "Full name must be at most 80 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    // ponytail: bcrypt has a 72-char ceiling, no point accepting more
    @Size(max = 72, message = "Password must be at most 72 characters")
    private String password;

    public boolean isDisposableEmail() {
        return DisposableEmailValidator.isDisposable(email);
    }
}
