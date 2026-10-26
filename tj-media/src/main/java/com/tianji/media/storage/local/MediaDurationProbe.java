package com.tianji.media.storage.local;

import java.nio.file.Path;

/** Read presentation duration from uploaded bytes, never from a browser form. */
@FunctionalInterface
public interface MediaDurationProbe {
    float seconds(Path file, String filename);
}
