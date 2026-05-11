package com.example.situation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeFileNameResolverTest {

    @Test
    void buildDownloadFilenameUsesBePrefixWithCanonicalNumber() {
        assertEquals("BE_15.pdf", BeFileNameResolver.buildDownloadFilename("BE 15"));
        assertEquals("BE_18.pdf", BeFileNameResolver.buildDownloadFilename("18.pdf"));
        assertEquals("BE_7.pdf", BeFileNameResolver.buildDownloadFilename("BE_07.pdf"));
    }

    @Test
    void buildDownloadFilenamePreservesNonNumericTokens() {
        assertEquals("BE_LF.pdf", BeFileNameResolver.buildDownloadFilename("LF"));
    }
}
