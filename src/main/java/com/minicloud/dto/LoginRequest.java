
package com.minicloud.dto;

public record LoginRequest(
        String uniqueUserId,
        String password
) {
}
