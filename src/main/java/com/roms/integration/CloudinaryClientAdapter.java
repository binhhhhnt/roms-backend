package com.roms.integration;

public interface CloudinaryClientAdapter {
    String upload(byte[] bytes, String publicId);
    void delete(String publicId);
}
