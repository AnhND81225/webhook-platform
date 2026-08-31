package com.webhookplatform.webhook.signature;

record EncryptedSecret(byte[] ciphertext, byte[] nonce, int keyVersion) { }
