package com.incidentpilot.knowledge.ingestion;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
class DocumentContentHasher {

    String sha256(List<ChunkInput> chunks) {
        MessageDigest digest = sha256Digest();

        for (ChunkInput chunk : chunks) {
            byte[] content = chunk.content().getBytes(StandardCharsets.UTF_8);
            digest.update(intBytes(chunk.chunkIndex()));
            digest.update(intBytes(content.length));
            digest.update(content);
        }

        return HexFormat.of().formatHex(digest.digest());// HexFormat把不好阅读的二进制 byte 数组转成十六进制字符串 SHA-256 最终会得到 64 个十六进制字符
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array(); //这是把一个 Java int 转成 4 个字节
    }
}
