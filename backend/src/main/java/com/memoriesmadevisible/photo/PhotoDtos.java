package com.memoriesmadevisible.photo;

public final class PhotoDtos {
    private PhotoDtos() {}

    /** A published photo as the site renders it: full-size src, responsive srcset (null for older uploads), full width. */
    public record PhotoUrls(String src, String srcset, Integer width) {}
}
