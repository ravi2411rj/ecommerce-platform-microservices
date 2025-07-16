package com.ecommerce.userservice;

import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;

import java.security.Key;

public class KeyGenerator {
    public static void main(String[] args) {
        Key key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS512); // HS256. Use HS512 for stronger
        String base64Key = Encoders.BASE64.encode(key.getEncoded());
        System.out.println(base64Key);
    }
}
