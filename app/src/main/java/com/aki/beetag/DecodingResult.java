package com.aki.beetag;

import java.util.List;

public class DecodingResult {
    public static final int OK = 0;
    public static final int TAG_NOT_FOUND = 1;
    public static final int COMMUNICATION_FAILED = 2;
    public static final int UNKNOWN_HOST = 3;
    public static final int UNEXPECTED_RESPONSE = 4;
    public static final int UNKNOWN_ERROR = 10;

    public DecodingData input;
    public List<Tag> decodedTags;
    public int resultCode;

    public DecodingResult(DecodingData input, List<Tag> decodedTags, int resultCode) {
        this.input = input;
        this.decodedTags = decodedTags;
        this.resultCode = resultCode;
    }
}
